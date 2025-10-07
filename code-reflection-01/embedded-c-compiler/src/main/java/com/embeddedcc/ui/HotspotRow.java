package com.embeddedcc.ui;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

class HotspotRow {

    private final IntegerProperty id = new SimpleIntegerProperty();
    private final IntegerProperty line = new SimpleIntegerProperty();
    private final StringProperty expression = new SimpleStringProperty();
    private final IntegerProperty misses = new SimpleIntegerProperty();
    private final IntegerProperty evictions = new SimpleIntegerProperty();
    private final IntegerProperty score = new SimpleIntegerProperty();

    HotspotRow(int id, Integer line, String expression, int misses, int evictions, int score) {
        this.id.set(id);
        if (line != null) {
            this.line.set(line);
        }
        this.expression.set(expression != null ? expression : "-");
        this.misses.set(misses);
        this.evictions.set(evictions);
        this.score.set(score);
    }

    int getId() {
        return id.get();
    }

    IntegerProperty idProperty() {
        return id;
    }

    int getLine() {
        return line.get();
    }

    IntegerProperty lineProperty() {
        return line;
    }

    String getExpression() {
        return expression.get();
    }

    StringProperty expressionProperty() {
        return expression;
    }

    int getMisses() {
        return misses.get();
    }

    IntegerProperty missesProperty() {
        return misses;
    }

    int getEvictions() {
        return evictions.get();
    }

    IntegerProperty evictionsProperty() {
        return evictions;
    }

    int getScore() {
        return score.get();
    }

    IntegerProperty scoreProperty() {
        return score;
    }
}

