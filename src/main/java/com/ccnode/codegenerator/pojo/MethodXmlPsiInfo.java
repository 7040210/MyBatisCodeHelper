package com.ccnode.codegenerator.pojo;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

/**
 * Created by bruce.ge on 2016/12/12.
 */
public class MethodXmlPsiInfo {

    private String methodName;

    private String returnClassName;

    private FieldToColumnRelation relation;

    private PsiClass pojoClass;

    private String tableName;

    public FieldToColumnRelation getRelation() {
        return relation;
    }

    public void setRelation(FieldToColumnRelation relation) {
        this.relation = relation;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public PsiClass getPojoClass() {
        return pojoClass;
    }

    public void setPojoClass(PsiClass pojoClass) {
        this.pojoClass = pojoClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getReturnClassName() {
        return returnClassName;
    }

    public void setReturnClassName(String returnClassName) {
        this.returnClassName = returnClassName;
    }
}
