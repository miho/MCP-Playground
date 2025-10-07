package com.embeddedcc.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

class CandidateRow {
    private final IntegerProperty index = new SimpleIntegerProperty();
    private final StringProperty expression = new SimpleStringProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final IntegerProperty line = new SimpleIntegerProperty();
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    CandidateRow(int index, String expression, String type, int line) {
        this.index.set(index);
        this.expression.set(expression);
        this.type.set(type);
        this.line.set(line);
    }

    int getIndex() {
        return index.get();
    }

    IntegerProperty indexProperty() {
        return index;
    }

    String getExpression() {
        return expression.get();
    }

    StringProperty expressionProperty() {
        return expression;
    }

    String getType() {
        return type.get();
    }

    StringProperty typeProperty() {
        return type;
    }

    int getLine() {
        return line.get();
    }

    IntegerProperty lineProperty() {
        return line;
    }

    boolean isSelected() {
        return selected.get();
    }

    void setSelected(boolean value) {
        selected.set(value);
    }

    BooleanProperty selectedProperty() {
        return selected;
    }
}

