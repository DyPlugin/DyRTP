// Vendored from bStats Metrics 3.2.1 (MIT License, see licenses/bstats-LICENSE.txt); package relocated for DyRTP.
package dev.dyrtp.lib.bstats.charts;

import dev.dyrtp.lib.bstats.json.JsonObjectBuilder;

import java.util.Map;
import java.util.concurrent.Callable;

public class DrilldownPie extends CustomChart {

    private final Callable<Map<String, Map<String, Integer>>> callable;

    /**
     * Class constructor.
     *
     * @param chartId The id of the chart.
     * @param callable The callable which is used to request the chart data.
     */
    public DrilldownPie(String chartId, Callable<Map<String, Map<String, Integer>>> callable) {
        super(chartId);
        this.callable = callable;
    }

    @Override
    public JsonObjectBuilder.JsonObject getChartData() throws Exception {
        JsonObjectBuilder valuesBuilder = new JsonObjectBuilder();

        Map<String, Map<String, Integer>> map = callable.call();
        if (map == null || map.isEmpty()) {
            // Null = skip the chart
            return null;
        }
        boolean reallyAllSkipped = true;
        for (Map.Entry<String, Map<String, Integer>> entryValues : map.entrySet()) {
            JsonObjectBuilder valueBuilder = new JsonObjectBuilder();
            boolean allSkipped = true;
            for (Map.Entry<String, Integer> valueEntry : map.get(entryValues.getKey()).entrySet()) {
                valueBuilder.appendField(valueEntry.getKey(), valueEntry.getValue());
                allSkipped = false;
            }
            if (!allSkipped) {
                reallyAllSkipped = false;
                valuesBuilder.appendField(entryValues.getKey(), valueBuilder.build());
            }
        }
        if (reallyAllSkipped) {
            // Null = skip the chart
            return null;
        }

        return new JsonObjectBuilder()
                .appendField("values", valuesBuilder.build())
                .build();
    }
}