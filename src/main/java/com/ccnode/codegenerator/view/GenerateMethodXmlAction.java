package com.ccnode.codegenerator.view;

import com.ccnode.codegenerator.constants.MapperConstants;
import com.ccnode.codegenerator.constants.MyBatisXmlConstants;
import com.ccnode.codegenerator.database.DatabaseComponenent;
import com.ccnode.codegenerator.dialog.ChooseXmlToUseDialog;
import com.ccnode.codegenerator.dialog.GenerateResultMapDialog;
import com.ccnode.codegenerator.dialog.MapperUtil;
import com.ccnode.codegenerator.dialog.MethodExistDialog;
import com.ccnode.codegenerator.log.Log;
import com.ccnode.codegenerator.log.LogFactory;
import com.ccnode.codegenerator.methodnameparser.QueryParseDto;
import com.ccnode.codegenerator.methodnameparser.QueryParser;
import com.ccnode.codegenerator.methodnameparser.buidler.ParamInfo;
import com.ccnode.codegenerator.methodnameparser.buidler.QueryInfo;
import com.ccnode.codegenerator.methodnameparser.tag.XmlTagAndInfo;
import com.ccnode.codegenerator.pojo.FieldToColumnRelation;
import com.ccnode.codegenerator.pojo.MethodXmlPsiInfo;
import com.ccnode.codegenerator.util.*;
import com.google.common.base.Stopwatch;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by bruce.ge on 2016/12/5.
 */
public class GenerateMethodXmlAction extends PsiElementBaseIntentionAction {

    public static final String JAVALIST = "java.util.List";
    public static final String GENERATE_DAOXML = "generate daoxml";
    public static final String INSERT_INTO = "insert into";
    public static final String DAOCLASSEND = "Dao";

    private static Log log = LogFactory.getLogger(GenerateMethodXmlAction.class);

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        Stopwatch started = Stopwatch.createStarted();
        Module srcModule = ModuleUtilCore.findModuleForPsiElement(element);
        if (srcModule == null) {
            return;
        }
        PsiClass srcClass = PsiElementUtil.getContainingClass(element);
        if (srcClass == null) return;
        //go to check if the pojo class exist.
        PsiClass pojoClass = PsiClassUtil.getPojoClass(srcClass);
        String srcClassName = srcClass.getName();
        //ask user to provide a class name for it.
        if (pojoClass == null) {
            Messages.showErrorDialog("please provide an insert method with corresponding database class as parameter in this class" +
                    "\n like 'insert(User user)'\n" +
                    "we need the 'User' class to parse your method", "can't find the class for the database table");
            return;
        }

//        PsiDirectory srcDir = element.getContainingFile().getContainingDirectory();
//        PsiPackage srcPackage = JavaDirectoryService.getInstance().getPackage(srcDir);
        PsiElement parent = element.getParent();
        MethodXmlPsiInfo methodInfo = new MethodXmlPsiInfo();
        if (parent instanceof PsiMethod) {
            return;
        } else if (parent instanceof PsiJavaCodeReferenceElement) {
            String text = parent.getText();
            methodInfo.setMethodName(text);
//
//            return;
        } else if (element instanceof PsiWhiteSpace) {
            PsiElement lastMatchedElement = findLastMatchedElement(element);
            element = lastMatchedElement;
            String text = lastMatchedElement.getText();
            methodInfo.setMethodName(text);
        }


        //when pojoClass is not null, then try to extract all property from it. then get the sql generated.do thing with batch.

        String xmlFileName = srcClassName + ".xml";
        XmlFile psixml = null;
        //todo use with module?
        PsiFile[] filesByName = PsiShortNamesCache.getInstance(project).getFilesByName(xmlFileName);
        if (filesByName.length > 0) {
            for (PsiFile file : filesByName) {
                if (file instanceof XmlFileImpl) {
                    XmlFileImpl xmlFile = (XmlFileImpl) file;
                    XmlTag rootTag = xmlFile.getRootTag();
                    String namespace = rootTag.getAttribute("namespace").getValue();
                    //only the name space is equal than deal with it.
                    if (namespace != null && namespace.equals(srcClass.getQualifiedName())) {
                        psixml = xmlFile;
                        break;
                    }
                }
            }
        }
        if (psixml == null) {
            //cant' find the file by name. then go to search it. will only search the file once.
            List<XmlFile> xmlFiles = PsiSearchUtils.searchMapperXml(project, ModuleUtilCore.findModuleForPsiElement(element), srcClass.getQualifiedName());
            if (xmlFiles.size() == 0) {
                Messages.showErrorDialog("can't find xml file for namespace " + srcClassName, "xml file not found error");
                return;
            } else if (xmlFiles.size() == 1) {
                psixml = xmlFiles.get(0);
            }
        }
        //extract field from pojoClass.
        List<String> props = PsiClassUtil.extractProps(pojoClass);
        //find the corresponding xml file.
        XmlTag rootTag = psixml.getRootTag();
        if (rootTag == null) {
            return;
        }
        XmlTag[] subTags = rootTag.getSubTags();

        boolean allColumMapExist = false;
        boolean allColumns = false;

        //boolean isReturnclassCurrentClass.
        String tableName = null;

        FieldToColumnRelation relation = null;
        boolean hasResultType = false;
        for (XmlTag tag : subTags) {
            if (tag.getName().equalsIgnoreCase(MyBatisXmlConstants.INSERT)) {
                String insertText = tag.getValue().getText();
                //go format it.
                tableName = MapperUtil.extractTable(insertText);
                if (tableName != null && tableName.length() < 30) {
                    break;
                }
            } else if (relation == null && tag.getName().equalsIgnoreCase(MyBatisXmlConstants.RESULTMAP)) {
                String resultMapId;
                XmlAttribute id = tag.getAttribute(MyBatisXmlConstants.ID);
                if (id != null && id.getValue() != null) {
                    resultMapId = id.getValue();
                    XmlAttribute typeAttribute = tag.getAttribute(MyBatisXmlConstants.TYPE);
                    if (typeAttribute != null && typeAttribute.getValue() != null && typeAttribute.getValue().trim().equals(pojoClass.getQualifiedName())) {
                        //mean we find the corresponding prop.
                        hasResultType = true;
                        relation = extractFieldAndColumnRelation(tag, props, resultMapId);
                    }
                }
            } else if (tag.getName().equalsIgnoreCase(MyBatisXmlConstants.SQL)) {
                XmlAttribute id = tag.getAttribute(MyBatisXmlConstants.ID);
                if (id != null && id.getValue().equals(MapperConstants.ALL_COLUMN)) {
                    allColumns = true;
                }
            }
            //then go next shall be the same.
            //deal with it.
        }

        if (StringUtils.isEmpty(tableName)) {
            Messages.showErrorDialog("can't find table name from your " + psixml.getName() + "" +
                    "\nplease add a correct insert method into the file\n" +
                    "like\n'<insert id=\"insert\">\n" +
                    "        INSERT INTO user ....\n</insert>\n" +
                    "so we can extract the table name 'user' from it", "can't extract table name");
            return;
        }

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        if (relation == null) {
            if (hasResultType) {
                Messages.showErrorDialog("please check with your resultMap\n" +
                        "dose it contain all the property of " + pojoClass.getQualifiedName() + "? ", "proprety in resultMap is not complete");
                return;
            } else {
                GenerateResultMapDialog generateResultMapDialog = new GenerateResultMapDialog(project, props, pojoClass.getQualifiedName());
                boolean b = generateResultMapDialog.showAndGet();
                if (!b) {
                    return;
                }
//                Messages.showErrorDialog("please provide a resultMap the type is:" + pojoClass.getQualifiedName() + "\n" +
//                        "in xml path:" + psixml.getVirtualFile().getPath(), "can't find resultMap in your mapper xml");
                //create tag into the file.
                FieldToColumnRelation relation1 = generateResultMapDialog.getRelation();
                //use to generate resultMap
                String allColumnMap = buildAllCoumnMap(relation1.getFiledToColumnMap());
                XmlTag resultMap = rootTag.createChildTag(MyBatisXmlConstants.RESULTMAP, "", allColumnMap, false);
                resultMap.setAttribute(MyBatisXmlConstants.ID, relation1.getResultMapId());
                resultMap.setAttribute(MyBatisXmlConstants.TYPE, pojoClass.getQualifiedName());
                rootTag.addSubTag(resultMap, true);
                Document xmlDocument = psiDocumentManager.getDocument(psixml);
                PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmlDocument);

                relation = convertToRelation(relation1);
            }
        }

        methodInfo.setRelation(relation);

        if (!allColumns) {
            String allColumn = buildAllColumn(relation.getFiledToColumnMap());
            XmlTag sql = rootTag.createChildTag("sql", "", allColumn, false);
            sql.setAttribute("id", MapperConstants.ALL_COLUMN);
            rootTag.addSubTag(sql, true);
        }

        XmlTag existTag
                = methodAlreadyExist(psixml, methodInfo.getMethodName());

        if (existTag != null) {
            MethodExistDialog exist = new MethodExistDialog(project, existTag.getText());
            boolean b = exist.showAndGet();
            if (!b) {
                return;
            } else {
                existTag.delete();
            }
        }
        rootTag = psixml.getRootTag();
        methodInfo.setTableName(tableName);
        methodInfo.setPsiClassFullName(pojoClass.getQualifiedName());
        methodInfo.setPsiClassName(pojoClass.getName());
        methodInfo.setFieldMap(PsiClassUtil.buildFieldMapWithConvertPrimitiveType(pojoClass));
        QueryParseDto parseDto = QueryParser.parse(props, methodInfo);
        XmlTagAndInfo choosed = null;
        if (parseDto.getHasMatched()) {
            //dothings in it.
            List<QueryInfo> queryInfos = parseDto.getQueryInfos();
            //generate tag for them
            List<XmlTagAndInfo> tags = new ArrayList<>();
            for (QueryInfo info : queryInfos) {
                XmlTagAndInfo tag = generateTag(rootTag, info, methodInfo.getMethodName());
                tags.add(tag);
            }

            if (tags.size() > 1) {
                //let user choose with one.
                ChooseXmlToUseDialog chooseXmlToUseDialog = new ChooseXmlToUseDialog(project, tags);
                boolean b = chooseXmlToUseDialog.showAndGet();
                if (!b) {
                    return;
                } else {
                    choosed = tags.get(chooseXmlToUseDialog.getChoosedIndex());
                }

            } else {
                choosed = tags.get(0);
            }
        } else {
            //there is no match the current methodName display error msg for user.
            String content = "";
            List<String> errorMsg = parseDto.getErrorMsg();
            for (int i = 0; i < errorMsg.size(); i++) {
                content += errorMsg.get(i) + "\n";
            }
            Messages.showErrorDialog(content, "can't parse the methodName");
            return;
        }

        //means we need to insert the text into it.
        String insertBefore = choosed.getInfo().getMethodReturnType() + " ";
        String insertNext = "(";
        if (choosed.getInfo().getParamInfos() != null) {
            for (int i = 0; i < choosed.getInfo().getParamInfos().size(); i++) {
                ParamInfo info = choosed.getInfo().getParamInfos().get(i);
                insertNext += "@Param(\"" + info.getParamAnno() + "\")" + info.getParamType() + " " + info.getParamValue();
                if (i != choosed.getInfo().getParamInfos().size() - 1) {
                    insertNext += ",";
                }
            }
        }
        insertNext += ");";
        //insert text into it.
        Document document = psiDocumentManager.getDocument(srcClass.getContainingFile());
        document.insertString(element.getTextOffset(), insertBefore);
        document.insertString(element.getTextOffset() + element.getTextLength() + insertBefore.length(), insertNext);
        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, document);

        psixml.getRootTag().addSubTag(choosed.getXmlTag(), false);

        Document xmlDocument = psiDocumentManager.getDocument(psixml);
        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmlDocument);

        XmlTag[] newSubTags = psixml.getRootTag().getSubTags();
        XmlTag newSubTag = newSubTags[newSubTags.length - 1];
        xmlDocument.insertString(newSubTag.getTextOffset(), "\n<!--auto generated by codehelper on " + DateUtil.formatLong(new Date()) + "-->\n" + GenCodeUtil.ONE_RETRACT);
        PsiDocumentUtils.commitAndSaveDocument(psiDocumentManager, xmlDocument);
        Set<String> importList = choosed.getInfo().getImportList();
        PsiDocumentUtils.addImportToFile(psiDocumentManager, (PsiJavaFile) srcClass.getContainingFile(), psiDocumentManager.getDocument(srcClass.getContainingFile()), importList);
        CodeInsightUtil.positionCursor(project, psixml, rootTag.getSubTags()[rootTag.getSubTags().length - 1]);
        long elapsed = started.elapsed(TimeUnit.MILLISECONDS);
        log.info("generate dao xml use with time in mill second is:" + elapsed + " and the method name is:" + methodInfo.getMethodName()
                + " used database is:" + DatabaseComponenent.currentDatabase());
    }

    private FieldToColumnRelation convertToRelation(FieldToColumnRelation relation1) {
        FieldToColumnRelation relation = new FieldToColumnRelation();
        relation.setResultMapId(relation1.getResultMapId());
        Map<String, String> fieldToColumnLower = new LinkedHashMap<>();
        for (String prop : relation1.getFiledToColumnMap().keySet()) {
            fieldToColumnLower.put(prop.toLowerCase(), relation1.getFiledToColumnMap().get(prop));
        }
        relation.setFiledToColumnMap(fieldToColumnLower);
        return relation;
    }

    private FieldToColumnRelation extractFieldAndColumnRelation(XmlTag tag, List<String> props, String resultMapId) {
        Set<String> propSet = new HashSet<>(props);
        XmlTag[] subTags = tag.getSubTags();
        if (subTags == null || subTags.length == 0) {
            return null;
        }
        Map<String, String> fieldAndColumnMap = new LinkedHashMap<>();
        for (XmlTag propTag : subTags) {
            XmlAttribute column = propTag.getAttribute("column");
            XmlAttribute property = propTag.getAttribute("property");
            if (column == null || column.getValue() == null || property == null || property.getValue() == null) {
                continue;
            }
            String columnString = column.getValue().trim();
            String propertyString = property.getValue().trim();
            if (!propSet.contains(propertyString)) {
                continue;
            }
            fieldAndColumnMap.put(propertyString.toLowerCase(), columnString);
            propSet.remove(propertyString);
        }
        //mean there are not all property in the resultMap.
        if (propSet.size() != 0) {
            return null;
        }
        FieldToColumnRelation relation = new FieldToColumnRelation();
        relation.setFiledToColumnMap(fieldAndColumnMap);
        relation.setResultMapId(resultMapId);
        return relation;
    }

    private XmlTagAndInfo generateTag(XmlTag rootTag, QueryInfo info, String methodName) {
        XmlTagAndInfo xmlTagAndInfo = new XmlTagAndInfo();
        xmlTagAndInfo.setInfo(info);
        XmlTag select = rootTag.createChildTag(info.getType(), "", info.getSql(), false);
        select.setAttribute("id", methodName);
        if (info.getReturnMap() != null) {
            select.setAttribute(MyBatisXmlConstants.RESULTMAP, info.getReturnMap());
        } else if (info.getReturnClass() != null) {
            select.setAttribute(MyBatisXmlConstants.RESULT_TYPE, info.getReturnClass());
        }
        xmlTagAndInfo.setXmlTag(select);
        return xmlTagAndInfo;

    }


    private String buildAllColumn(Map<String, String> filedToColumnMap) {
        StringBuilder bu = new StringBuilder();
        int i = 0;
        for (String s : filedToColumnMap.keySet()) {
            i++;
            bu.append("\n" + GenCodeUtil.ONE_RETRACT).append("`" + filedToColumnMap.get(s) + "`");
            if (i != filedToColumnMap.size()) {
                bu.append(",");
            }
        }
        bu.append("\n");
        return bu.toString();
    }

    private String buildAllCoumnMap(Map<String, String> fieldToColumnMap) {
        StringBuilder builder = new StringBuilder();
        for (String prop : fieldToColumnMap.keySet()) {
            builder.append("\n" + GenCodeUtil.ONE_RETRACT).append("<result column=\"").append(fieldToColumnMap.get(prop)).append("\"")
                    .append(" property=\"").append(prop).append("\"/>");
        }
        builder.append("\n");
        return builder.toString();
    }

    private static XmlTag methodAlreadyExist(PsiFile psixml, String methodName) {
        XmlTag rootTag = ((XmlFileImpl) psixml).getRootTag();
        XmlTag[] subTags = rootTag.getSubTags();
        for (XmlTag subTag : subTags) {
            XmlAttribute id = subTag.getAttribute(MyBatisXmlConstants.ID);
            if (id != null && id.getValue() != null && id.getValue().equalsIgnoreCase(methodName)) {
                return subTag;
            }
        }
        return null;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        if (!isAvailbleForElement(element)) return false;
        PsiClass containingClass = PsiElementUtil.getContainingClass(element);
        assert containingClass != null;
        PsiElement leftBrace = containingClass.getLBrace();
        if (leftBrace == null) {
            return false;
        }
        if (element instanceof PsiMethod) {
            return false;
        }
        PsiElement parent = element.getParent();
        if (parent instanceof PsiMethod) {
            return false;
        }
        if (element instanceof PsiWhiteSpace) {
            PsiElement element1 = findLastMatchedElement(element);
            if (element1 == null) {
                return false;
            }
            return true;
        }
        if (parent instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) parent;
            String text = referenceElement.getText().toLowerCase();
            if (MethodNameUtil.checkValidTextStarter(text)) {
                return true;
            }
        }
        return false;
    }

    private PsiElement findLastMatchedElement(PsiElement element) {
        PsiElement prevSibling = element.getPrevSibling();
        while (prevSibling != null && isIgnoreText(prevSibling.getText())) {
            prevSibling = prevSibling.getPrevSibling();
        }
        if (prevSibling != null) {
            String lowerCase = prevSibling.getText().toLowerCase();
            if (MethodNameUtil.checkValidTextStarter(lowerCase)) {
                return prevSibling;
            }
        }
        return null;
    }

    private boolean isIgnoreText(String text) {
        return (text.equals("")) || (text.equals("\n")) || text.equals(" ");
    }

    @NotNull
    @Override
    public String getText() {
        return GENERATE_DAOXML;
    }

    public static boolean isAvailbleForElement(PsiElement psiElement) {
        if (psiElement == null) {
            return false;
        }

        PsiClass containingClass = PsiElementUtil.getContainingClass(psiElement);
        if (containingClass == null) return false;
        Module srcMoudle = ModuleUtilCore.findModuleForPsiElement(containingClass);
        if (srcMoudle == null) return false;
        if (containingClass.isAnnotationType() || containingClass instanceof PsiAnonymousClass || !containingClass.isInterface()) {
            return false;
        }
        return true;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return GENERATE_DAOXML;
    }
}
