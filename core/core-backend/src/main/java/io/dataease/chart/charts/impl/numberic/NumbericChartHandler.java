package io.dataease.chart.charts.impl.numberic;

import io.dataease.chart.charts.impl.DefaultChartHandler;
import io.dataease.chart.utils.ChartDataBuild;
import io.dataease.dataset.dto.DatasourceSchemaDTO;
import io.dataease.dataset.utils.SqlUtils;
import io.dataease.datasource.provider.CalciteProvider;
import io.dataease.datasource.request.DatasourceRequest;
import io.dataease.engine.sql.SQLProvider;
import io.dataease.engine.trans.Quota2SQLObj;
import io.dataease.engine.utils.Utils;
import io.dataease.exception.DEException;
import io.dataease.extensions.view.dto.*;
import io.dataease.extensions.view.model.SQLMeta;
import io.dataease.extensions.view.util.FieldUtil;
import io.dataease.i18n.Translator;
import io.dataease.utils.BeanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NumbericChartHandler extends DefaultChartHandler {
    @Override
    public <T extends ChartCalcDataResult> T calcChartResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, Map<String, Object> sqlMap, SQLMeta sqlMeta, CalciteProvider provider) {
        var dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);
        boolean crossDs = Utils.isCrossDs(dsMap);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(dsMap);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        var allFields = getAllChartFields(view);
        Quota2SQLObj.quota2sqlObj(sqlMeta, yAxis, FieldUtil.transFields(allFields), crossDs, dsMap);
        String querySql = SQLProvider.createQuerySQL(sqlMeta, true, needOrder, view);
        querySql = SqlUtils.rebuildSQL(querySql, sqlMeta, crossDs, dsMap);
        datasourceRequest.setQuery(querySql);
        List<String[]> data = (List<String[]>) provider.fetchResultField(datasourceRequest).get("data");
        boolean isdrill = filterResult
                .getFilterList()
                .stream()
                .anyMatch(ele -> ele.getFilterType() == 1);
        Map<String, Object> result = ChartDataBuild.transNormalChartData(xAxis, yAxis, view, data, isdrill);
        T calcResult = (T) new ChartCalcDataResult();
        calcResult.setData(result);
        calcResult.setContext(filterResult.getContext());
        calcResult.setQuerySql(querySql);
        calcResult.setOriginData(data);
        return calcResult;
    }

    protected ChartViewFieldDTO getDynamicField(Map<String, Object> target, String type, String field) {
        String maxType = (String) target.get(type);
        if (StringUtils.equalsIgnoreCase("dynamic", maxType)) {
            Map<String, Object> maxField = (Map<String, Object>) target.get(field);
            Long id = Long.valueOf((String) maxField.get("id"));
            String summary = (String) maxField.get("summary");
            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(id);
            if (ObjectUtils.isNotEmpty(datasetTableField)) {
                if (datasetTableField.getDeType() == 0 || datasetTableField.getDeType() == 1 || datasetTableField.getDeType() == 5) {
                    if (!StringUtils.containsIgnoreCase(summary, "count")) {
                        DEException.throwException(Translator.get("i18n_gauge_field_change"));
                    }
                }
                ChartViewFieldDTO dto = new ChartViewFieldDTO();
                BeanUtils.copyBean(dto, datasetTableField);
                dto.setSummary(summary);
                return dto;
            } else {
                DEException.throwException(Translator.get("i18n_gauge_field_delete"));
            }
        }
        return null;
    }
}
