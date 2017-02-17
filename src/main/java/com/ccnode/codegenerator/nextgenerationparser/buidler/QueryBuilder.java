package com.ccnode.codegenerator.nextgenerationparser.buidler;

import com.ccnode.codegenerator.constants.MapperConstants;
import com.ccnode.codegenerator.constants.QueryTypeConstants;
import com.ccnode.codegenerator.nextgenerationparser.KeyWordConstants;
import com.ccnode.codegenerator.nextgenerationparser.QueryParseDto;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.base.ParsedErrorBase;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.base.QueryRule;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.count.ParsedCount;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.count.ParsedCountError;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.delete.ParsedDelete;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.delete.ParsedDeleteError;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.find.OrderByRule;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.find.ParsedFind;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.find.ParsedFindError;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.update.ParsedUpdate;
import com.ccnode.codegenerator.nextgenerationparser.parsedresult.update.ParsedUpdateError;
import com.ccnode.codegenerator.pojo.FieldToColumnRelation;
import com.ccnode.codegenerator.pojo.MethodXmlPsiInfo;
import com.ccnode.codegenerator.util.GenCodeUtil;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by bruce.ge on 2016/12/12.
 */
public class QueryBuilder {

    public static QueryParseDto buildFindResult(List<ParsedFind> parsedFinds, List<ParsedFindError> errors, MethodXmlPsiInfo info) {
        if (parsedFinds.size() == 0) {
            QueryParseDto dto = new QueryParseDto();
            dto.setHasMatched(false);
            List<String> errorMsgs = new ArrayList<>();
            for (ParsedFindError error : errors) {
                String errorMsg = buildErrorMsg(error);
                errorMsgs.add(errorMsg);
            }
            dto.setErrorMsg(errorMsgs);
            return dto;
        }

        //get pojo class all fields and their type do it cool.

        List<QueryInfo> queryInfos = new ArrayList<>();
        for (ParsedFind find : parsedFinds) {
            queryInfos.add(buildQueryInfo(find, info.getFieldMap(), info.getTableName(), info.getPsiClassName(), info.getRelation()));
        }
        //say this is not an method.
        QueryParseDto dto = new QueryParseDto();
        dto.setQueryInfos(queryInfos);
        if (queryInfos.size() > 0) {
            dto.setHasMatched(true);
        }
        return dto;
    }

    private static String buildErrorMsg(ParsedErrorBase error) {
        if (StringUtils.isEmpty(error.getRemaining())) {
            return "the query not end legal";
        } else {
            return "the remaining " + error.getRemaining() + " can't be parsed";
        }
    }

    private static QueryInfo buildQueryInfo(ParsedFind find, Map<String, String> fieldMap, String tableName, String pojoClassName, FieldToColumnRelation relation) {
        QueryInfo info = new QueryInfo();
        info.setType(QueryTypeConstants.SELECT);
        boolean queryAllTable = false;
        boolean returnList = true;
        if (find.getFetchProps() != null && find.getFetchProps().size() > 0) {
            if (find.getFetchProps().size() > 1) {
                info.setReturnMap(relation.getResultMapId());
            } else {
                //说明等于1
                String s = find.getFetchProps().get(0);
                info.setReturnClass(fieldMap.get(s));
            }
        } else {
            queryAllTable = true;
            info.setReturnMap(relation.getResultMapId());
        }
//later will check wether it is the same with method.
        if (find.getQueryRules() != null) {
            //will build with params.
            for (QueryRule rule : find.getQueryRules()) {
                String prop = rule.getProp();
                //say findById not return list.
                if (prop.toLowerCase().equals("id") && rule.getOperator() == null) {
                    returnList = false;
                }

            }
        }
        if (find.getLimit() == 1) {
            returnList = false;
        }

        if (info.getReturnClass() == null) {
            info.setReturnClass(pojoClassName);
        }

        if (returnList) {
            info.setMethodReturnType("List<" + extractLast(info.getReturnClass()) + ">");
        } else {
            info.setMethodReturnType(extractLast(info.getReturnClass()));
        }

        StringBuilder builder = new StringBuilder();
        //will notice it.
        if (queryAllTable) {
            builder.append("\n" + GenCodeUtil.ONE_RETRACT + "select <include refid=\"" + MapperConstants.ALL_COLUMN + "\"/>");
        } else {
            builder.append("\n" + GenCodeUtil.ONE_RETRACT + "select");
            if (find.getDistinct()) {
                builder.append(" distinct(");
                for (String prop : find.getFetchProps()) {
                    builder.append(" " + relation.getPropColumn(prop) + ",");
                }
                builder.deleteCharAt(builder.length() - 1);
                builder.append(")");
            } else {
                for (String prop : find.getFetchProps()) {
                    builder.append(" " + relation.getPropColumn(prop) + ",");
                }
                builder.deleteCharAt(builder.length() - 1);
            }
        }
        builder.append("\n" + GenCodeUtil.ONE_RETRACT + " from " + tableName);
        info.setSql(builder.toString());
        info.setParamInfos(new ArrayList<>());
        if (find.getQueryRules() != null) {
            buildQuerySqlAndParam(find.getQueryRules(), info, fieldMap, relation);
        }

        if (find.getOrderByProps() != null) {
            info.setSql(info.getSql() + " order by");
            for (OrderByRule rule : find.getOrderByProps()) {
                info.setSql(info.getSql() + " " + relation.getPropColumn(rule.getProp()) + " " + rule.getOrder());
            }
        }
        if (find.getLimit() > 0) {
            info.setSql(info.getSql() + " limit " + find.getLimit());
        }
        return info;
    }

    private static String extractLast(String returnClass) {
        int s = returnClass.lastIndexOf(".");
        return returnClass.substring(s + 1);
    }

    private static void buildQuerySqlAndParam(List<QueryRule> queryRules, QueryInfo info, Map<String, String> fieldMap, FieldToColumnRelation relation) {
        info.setSql(info.getSql() + "\n" + GenCodeUtil.ONE_RETRACT + "where");
        StringBuilder builder = new StringBuilder();
        for (QueryRule rule : queryRules) {
            String prop = rule.getProp();
            String operator = rule.getOperator();
            String connector = rule.getConnector();
            //mean =
            if (operator == null) {
                ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno(prop).withParamType(extractLast(fieldMap.get(prop))).withParamValue(prop).build();
                info.getParamInfos().add(paramInfo);
                builder.append(" " + relation.getPropColumn(prop) + "=#{" + paramInfo.getParamAnno() + "}");
            } else {
                switch (operator) {
                    case KeyWordConstants.GREATERTHAN: {
                        ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno("min" + firstCharUpper(prop)).withParamType(extractLast(fieldMap.get(prop))).withParamValue("min" + firstCharUpper(prop)).build();
                        info.getParamInfos().add(paramInfo);
                        builder.append(" " + relation.getPropColumn(prop) + cdata(">") + " #{" + paramInfo.getParamAnno() + "}");
                        break;
                    }

                    case KeyWordConstants.GREATERTHANOREQUALTO: {
                        ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno("min" + firstCharUpper(prop)).withParamType(extractLast(fieldMap.get(prop))).withParamValue("min" + firstCharUpper(prop)).build();
                        info.getParamInfos().add(paramInfo);
                        builder.append(" " + relation.getPropColumn(prop) + cdata(">=") + " #{" + paramInfo.getParamAnno() + "}");
                        break;
                    }
                    case KeyWordConstants.LESSTHAN: {
                        ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno("max" + firstCharUpper(prop)).withParamType(extractLast(fieldMap.get(prop))).withParamValue("max" + firstCharUpper(prop)).build();
                        info.getParamInfos().add(paramInfo);
                        builder.append(" " + relation.getPropColumn(prop) + cdata("<") + " #{" + paramInfo.getParamAnno() + "}");
                        break;
                    }

                    case KeyWordConstants.LESSTHANOREQUALTO: {
                        ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno("max" + firstCharUpper(prop)).withParamType(extractLast(fieldMap.get(prop))).withParamValue("max" + firstCharUpper(prop)).build();
                        info.getParamInfos().add(paramInfo);
                        builder.append(" " + relation.getPropColumn(prop) + cdata("<=") + " #{" + paramInfo.getParamAnno() + "}");
                        break;
                    }
                    case KeyWordConstants.BETWEEN: {
                        ParamInfo min = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno("min" + firstCharUpper(prop)).withParamType(extractLast(fieldMap.get(prop))).withParamValue("min" + firstCharUpper(prop)).build();
                        ParamInfo max = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno("max" + firstCharUpper(prop)).withParamType(extractLast(fieldMap.get(prop))).withParamValue("max" + firstCharUpper(prop)).build();
                        info.getParamInfos().add(min);
                        info.getParamInfos().add(max);
                        builder.append(" " + relation.getPropColumn(prop) + cdata(">=") + " #{" + min.getParamAnno() + "} and " + relation.getPropColumn(prop) + " " + cdata("<=") + " #{" + (max.getParamAnno()) + "}");
                        break;
                    }
                    case KeyWordConstants.ISNOTNULL: {
                        builder.append(" " + prop + " is not null");
                        break;
                    }
                    case KeyWordConstants.ISNULL: {
                        builder.append(" " + prop + " is null");
                        break;
                    }
                    case KeyWordConstants.NOT: {
                        ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno("not" + firstCharUpper(prop)).withParamType(extractLast(fieldMap.get(prop))).withParamValue("not" + firstCharUpper(prop)).build();
                        info.getParamInfos().add(paramInfo);
                        builder.append(" " + relation.getPropColumn(prop) + "<> #{" + paramInfo.getParamAnno() + "}");
                        break;
                    }
                    case KeyWordConstants.NOTIN: {
                        ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno(prop + "List").withParamType("List<" + extractLast(fieldMap.get(prop)) + ">").withParamValue(prop + "List").build();
                        info.getParamInfos().add(paramInfo);
                        builder.append(" " + relation.getPropColumn(prop) + " not in \n" + GenCodeUtil.ONE_RETRACT + "<foreach item=\"item\" index=\"index\" collection=\"" + paramInfo.getParamAnno() + "\"\n" + GenCodeUtil.ONE_RETRACT + "" +
                                "open=\"(\" separator=\",\" close=\")\">\n" + GenCodeUtil.ONE_RETRACT + "" +
                                "#{item}\n" + GenCodeUtil.ONE_RETRACT + "" +
                                "</foreach>\n");
                        break;
                    }
                    case KeyWordConstants.IN: {
                        ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno(prop + "List").withParamType("List<" + extractLast(fieldMap.get(prop)) + ">").withParamValue(prop + "List").build();
                        info.getParamInfos().add(paramInfo);
                        builder.append(" " + relation.getPropColumn(prop) + " in \n" + GenCodeUtil.ONE_RETRACT + "<foreach item=\"item\" index=\"index\" collection=\"" + paramInfo.getParamAnno() + "\"\n" + GenCodeUtil.ONE_RETRACT + "" +
                                "open=\"(\" separator=\",\" close=\")\">\n" + GenCodeUtil.ONE_RETRACT + "" +
                                "#{item}\n" + GenCodeUtil.ONE_RETRACT + "" +
                                "</foreach>\n");
                        break;
                    }
                    case KeyWordConstants.NOTLIKE: {
                        ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno("notlike" + firstCharUpper(prop)).withParamType(extractLast(fieldMap.get(prop))).withParamValue("notlike" + firstCharUpper(prop)).build();
                        info.getParamInfos().add(paramInfo);
                        builder.append(" " + relation.getPropColumn(prop) + "not like #{" + paramInfo.getParamAnno() + "}");
                        break;
                    }
                    case KeyWordConstants.LIKE: {
                        ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno("like" + firstCharUpper(prop)).withParamType(extractLast(fieldMap.get(prop))).withParamValue("like" + firstCharUpper(prop)).build();
                        info.getParamInfos().add(paramInfo);
                        builder.append(" " + relation.getPropColumn(prop) + "like #{" + paramInfo.getParamAnno() + "}");
                        break;
                    }

                }
            }
            if (connector != null) {
                builder.append(" " + connector);
            }
        }
        info.setSql(info.getSql() + builder.toString());
    }

    private static String firstCharUpper(String prop) {
        return prop.substring(0, 1).toUpperCase() + prop.substring(1);
    }


    public static String cdata(String s) {
        return "<![CDATA[" + s + "]]>";
    }


    public static void main(String[] args) {
        String propName = "java.lang.String";
        int i = propName.lastIndexOf(".");
        System.out.println(i);
    }


    public static QueryParseDto buildUpdateResult(List<ParsedUpdate> updateList, List<ParsedUpdateError> errorList, MethodXmlPsiInfo info) {
        if (updateList.size() == 0) {
            QueryParseDto dto = new QueryParseDto();
            dto.setHasMatched(false);
            List<String> errorMsgs = new ArrayList<>();
            for (ParsedUpdateError error : errorList) {
                errorMsgs.add(buildErrorMsg(error));
            }
            dto.setErrorMsg(errorMsgs);
            return dto;
        }

        List<QueryInfo> queryInfos = new ArrayList<>();
        for (ParsedUpdate update : updateList) {
            queryInfos.add(buildQueryUpdateInfo(update, info.getFieldMap(), info.getTableName(), info.getPsiClassName(), info.getRelation()));
        }
        QueryParseDto dto = new QueryParseDto();
        dto.setQueryInfos(queryInfos);
        dto.setHasMatched(true);
        return dto;
    }

    private static QueryInfo buildQueryUpdateInfo(ParsedUpdate update, Map<String, String> fieldMap, String tableName, String name, FieldToColumnRelation relation) {
        QueryInfo info = new QueryInfo();
        info.setType(QueryTypeConstants.UPDATE);
        info.setMethodReturnType("int");
        StringBuilder builder = new StringBuilder();
        builder.append("\n" + GenCodeUtil.ONE_RETRACT + "update " + tableName + "\n" + GenCodeUtil.ONE_RETRACT + "set");
        info.setParamInfos(new ArrayList<>());
        for (int i = 0; i < update.getUpdateProps().size(); i++) {
            String prop = update.getUpdateProps().get(i);
            ParamInfo paramInfo = ParamInfo.ParamInfoBuilder.aParamInfo().withParamAnno("updated" + firstCharUpper(prop)).
                    withParamType(extractLast(fieldMap.get(prop))).withParamValue("updated" + firstCharUpper(prop)).build();
            info.getParamInfos().add(paramInfo);
            builder.append(" " + relation.getPropColumn(prop) + "=#{" + paramInfo.getParamAnno() + "}");
            if (i != update.getUpdateProps().size() - 1) {
                builder.append(",");
            }
        }
        info.setSql(builder.toString());
        if (update.getQueryRules() != null) {
            buildQuerySqlAndParam(update.getQueryRules(), info, fieldMap, relation);
        }
        return info;
    }

    public static QueryParseDto buildDeleteResult(List<ParsedDelete> parsedDeletes, List<ParsedDeleteError> errors, MethodXmlPsiInfo info) {
        if (parsedDeletes.size() == 0) {
            QueryParseDto dto = new QueryParseDto();
            dto.setHasMatched(false);
            List<String> errorMsgs = new ArrayList<>();
            for (ParsedDeleteError error : errors) {
                errorMsgs.add(buildErrorMsg(error));
            }
            dto.setErrorMsg(errorMsgs);
        }

        List<QueryInfo> queryInfos = new ArrayList<>();
        for (ParsedDelete delete : parsedDeletes) {
            queryInfos.add(buildQueryDeleteInfo(delete, info.getFieldMap(), info.getTableName(), info.getPsiClassName(), info.getRelation()));
        }
        QueryParseDto dto = new QueryParseDto();
        dto.setQueryInfos(queryInfos);
        dto.setHasMatched(true);
        return dto;

    }

    private static QueryInfo buildQueryDeleteInfo(ParsedDelete delete, Map<String, String> fieldMap, String tableName, String name, FieldToColumnRelation relation) {
        QueryInfo info = new QueryInfo();
        info.setType(QueryTypeConstants.DELETE);
        info.setMethodReturnType("int");
        StringBuilder builder = new StringBuilder();
        builder.append("\n" + GenCodeUtil.ONE_RETRACT + "delete from  " + tableName);
        info.setParamInfos(new ArrayList<>());
        info.setSql(builder.toString());
        if (delete.getQueryRules() != null) {
            buildQuerySqlAndParam(delete.getQueryRules(), info, fieldMap, relation);
        }
        return info;
    }

    public static QueryParseDto buildCountResult(List<ParsedCount> parsedCounts, List<ParsedCountError> errors, MethodXmlPsiInfo info) {
        if (parsedCounts.size() == 0) {
            QueryParseDto dto = new QueryParseDto();
            dto.setHasMatched(false);
            List<String> errorMsgs = new ArrayList<>();
            for (ParsedCountError error : errors) {
                errorMsgs.add(buildErrorMsg(error));
            }
            dto.setErrorMsg(errorMsgs);
            return dto;
        }
        List<QueryInfo> queryInfos = new ArrayList<>();
        for (ParsedCount count : parsedCounts) {
            queryInfos.add(buildQueryCountInfo(count, info.getFieldMap(), info.getTableName(), info.getPsiClassName(), info.getRelation()));
        }
        QueryParseDto dto = new QueryParseDto();
        dto.setQueryInfos(queryInfos);
        dto.setHasMatched(true);
        return dto;

    }

    private static QueryInfo buildQueryCountInfo(ParsedCount count, Map<String, String> fieldMap, String tableName, String name, FieldToColumnRelation relation) {
        QueryInfo info = new QueryInfo();
        info.setType(QueryTypeConstants.SELECT);
        String idType = fieldMap.get("id");
        if (idType != null) {
            info.setReturnClass(idType);
            String returnType = extractLast(idType);
            info.setMethodReturnType(returnType);
        } else {
            info.setReturnClass("java.lang.Integer");
            info.setMethodReturnType("Integer");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("\n" + GenCodeUtil.ONE_RETRACT + "select count(");
        if (count.isDistinct()) {
            builder.append("distinct(");
            for (int i = 0; i < count.getFetchProps().size(); i++) {
                builder.append(relation.getPropColumn(count.getFetchProps().get(i)));
                if (i != count.getFetchProps().size() - 1) {
                    builder.append(",");
                }
            }
            builder.append(")");
        } else {
            if (count.getFetchProps() == null) {
                builder.append("1");
            } else {
                for (int i = 0; i < count.getFetchProps().size(); i++) {
                    builder.append(relation.getPropColumn(count.getFetchProps().get(i)));
                    if (i != count.getFetchProps().size() - 1) {
                        builder.append(",");
                    }
                }
            }
        }
        builder.append(")");
        builder.append("\n" + GenCodeUtil.ONE_RETRACT + "from " + tableName);
        info.setParamInfos(new ArrayList<>());
        info.setSql(builder.toString());
        if (count.getQueryRules() != null) {
            buildQuerySqlAndParam(count.getQueryRules(), info, fieldMap, relation);
        }
        return info;
    }
}
