package edu.campus.food.algorithm;

import edu.campus.food.auth.CurrentUser;
import edu.campus.food.catalog.CatalogMapper;
import edu.campus.food.common.ApiException;
import edu.campus.food.common.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TraceService {
    private final CatalogMapper catalog;
    private final CurrentUser current;
    private final AuditService audit;

    public TraceService(CatalogMapper catalog, CurrentUser current, AuditService audit) {
        this.catalog = catalog;
        this.current = current;
        this.audit = audit;
    }

    private void requireStaff() {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER", "PURCHASER", "CHEF");
    }

    public Map<String, Object> batchTrace(long batchId) {
        requireStaff();
        Map<String, Object> batch = catalog.batch(batchId);
        if (batch == null) throw new ApiException(HttpStatus.NOT_FOUND, "食材批次不存在");
        current.requireCanteen(((Number) batch.get("canteen_id")).longValue());
        List<Map<String, Object>> acceptances = catalog.acceptances(batchId);
        List<Map<String, Object>> usages = catalog.usagesByBatch(batchId);
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        String supplierNode = "supplier:" + batch.get("supplier_id");
        String batchNode = "batch:" + batchId;
        nodes.add(Map.of("id", supplierNode, "type", "supplier", "label", batch.get("supplier_name")));
        nodes.add(Map.of("id", batchNode, "type", "batch", "label", batch.get("batch_code")));
        edges.add(Map.of("from", supplierNode, "to", batchNode, "relation", "供货"));
        for (Map<String, Object> acceptance : acceptances) {
            String node = "acceptance:" + acceptance.get("id");
            nodes.add(Map.of("id", node, "type", "acceptance", "label", acceptance.get("result")));
            edges.add(Map.of("from", batchNode, "to", node, "relation", "验收"));
        }
        for (Map<String, Object> usage : usages) {
            String usageNode = "usage:" + usage.get("id");
            String menuNode = "menu:" + usage.get("menu_entry_id");
            nodes.add(Map.of("id", usageNode, "type", "usage", "label", String.valueOf(usage.get("quantity"))));
            nodes.add(Map.of("id", menuNode, "type", "menu", "label", usage.get("dish_name") + " / " + usage.get("service_date") + " / " + usage.get("meal")));
            edges.add(Map.of("from", batchNode, "to", usageNode, "relation", "领用"));
            edges.add(Map.of("from", usageNode, "to", menuNode, "relation", "制作供餐"));
        }
        Set<String> reachable = traverse(batchNode, edges);
        List<Map<String, Object>> affected = usages.stream()
                .filter(usage -> reachable.contains("menu:" + usage.get("menu_entry_id"))).toList();
        Map<String, Object> result = new HashMap<>();
        result.put("batch", batch);
        result.put("supplier", catalog.supplier(((Number) batch.get("supplier_id")).longValue()));
        result.put("acceptances", acceptances);
        result.put("affectedMenus", affected);
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("warnings", affected.isEmpty()
                ? List.of("尚未登记这批食材用于哪道菜。请在“菜品与供餐”中登记实际用料；空列表不代表食材一定没有使用。")
                : List.of("以下只显示已登记实际用料的供餐；未登记的使用记录不会出现在追溯结果中。"));
        audit.log("TRACE_BATCH", "purchase_batch", batchId, "affectedMenus=" + affected.size());
        return result;
    }

    public Map<String, Object> menuTrace(long menuId) {
        current.requireRole("ADMIN", "PRINCIPAL", "INSPECTOR", "MANAGER", "CHEF");
        Map<String, Object> menu = catalog.menu(menuId);
        if (menu == null) throw new ApiException(HttpStatus.NOT_FOUND, "供餐记录不存在");
        current.requireCanteen(((Number) menu.get("canteen_id")).longValue());
        if (current.has("CHEF") && !current.isSupervisor()
                && menu.get("published_at") == null) throw new ApiException(HttpStatus.NOT_FOUND, "食谱尚未发布");
        List<Map<String, Object>> usages = catalog.usagesByMenu(menuId);
        List<Map<String, Object>> ingredients = new ArrayList<>();
        for (Map<String, Object> usage : usages) {
            long batchId = ((Number) usage.get("batch_id")).longValue();
            Map<String, Object> item = new HashMap<>();
            item.put("usage", usage);
            item.put("batch", catalog.batch(batchId));
            item.put("acceptances", catalog.acceptances(batchId));
            ingredients.add(item);
        }
        audit.log("TRACE_MENU", "menu_entry", menuId, "batches=" + ingredients.size());
        return Map.of("menu", menu, "ingredients", ingredients,
                "warnings", ingredients.isEmpty() ? List.of("未记录实际使用的食材批次，无法完成反向追溯")
                        : List.of("仅展示已登记用料，缺失领用记录可能使反向追溯不完整"));
    }

    static Set<String> traverse(String start, List<Map<String, Object>> edges) {
        Map<String, List<String>> next = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            next.computeIfAbsent(String.valueOf(edge.get("from")), ignored -> new ArrayList<>())
                    .add(String.valueOf(edge.get("to")));
        }
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            for (String child : next.getOrDefault(queue.remove(), List.of())) {
                if (visited.add(child)) queue.add(child);
            }
        }
        return visited;
    }
}
