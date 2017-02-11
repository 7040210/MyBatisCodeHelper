package com.ccnode.codegenerator.dialog.datatype;

/**
 * Created by bruce.ge on 2016/12/25.
 */
public class ClassFieldInfo {
    private String fieldName;

//    the field type is Object type will convert int -> java.lang.Integer
    private String fieldType;

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }
}
