package com.embeddedcc.ui;

import javafx.scene.layout.BorderPane;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CodeView extends BorderPane {

    private final CodeArea codeArea = new CodeArea();
    private Set<Integer> highlightedLines = Set.of();

    public CodeView() {
        codeArea.setWrapText(false);
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        setCenter(new VirtualizedScrollPane<>(codeArea));
    }

    public void setCode(String code) {
        codeArea.replaceText(code == null ? "" : code);
    }

    public String getCode() {
        return codeArea.getText();
    }

    public void highlightLines(Set<Integer> lines) {
        if (lines == null) {
            lines = Collections.emptySet();
        }
        highlightedLines = new HashSet<>(lines);

        int paragraphCount = codeArea.getParagraphs().size();
        for (int i = 0; i < paragraphCount; i++) {
            int lineNumber = i + 1;
            if (highlightedLines.contains(lineNumber)) {
                codeArea.setParagraphStyle(i, Collections.singletonList("miss-line"));
            } else {
                codeArea.setParagraphStyle(i, Collections.emptyList());
            }
        }
    }

    public void focusLine(int line) {
        if (line < 1) {
            return;
        }
        int targetParagraph = Math.min(line - 1, Math.max(codeArea.getParagraphs().size() - 1, 0));
        codeArea.moveTo(targetParagraph, 0);
        codeArea.requestFollowCaret();
    }

    public void setEditable(boolean editable) {
        codeArea.setEditable(editable);
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }
}
