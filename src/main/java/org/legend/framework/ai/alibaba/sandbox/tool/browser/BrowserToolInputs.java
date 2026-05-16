package org.legend.framework.ai.alibaba.sandbox.tool.browser;


public final class BrowserToolInputs {

    private BrowserToolInputs() {}

    public record NavigateInput(
        String url
    ) {}

    public record ClickInput(
        String element,
        String ref
    ) {}

    public record TypeInput(
        String element,
        String ref,
        String text,
        Boolean submit,
        Boolean slowly
    ) {}

    public record ScreenshotInput(
        Boolean raw,
        String filename,
        String element,
        String ref
    ) {}

    public record SnapshotInput() {}

    public record TabNewInput(
        String url
    ) {}

    public record TabSelectInput(
        Integer index
    ) {}

    public record TabCloseInput(
        Integer index
    ) {}

    public record TabListInput() {}

    public record WaitForInput(
        Double time,
        String text,
        String textGone
    ) {}

    public record ResizeInput(
        Integer width,
        Integer height
    ) {}

    public record CloseInput() {}

    public record ConsoleMessagesInput() {}

    public record HandleDialogInput(
        Boolean accept,
        String promptText
    ) {}

    public record FileUploadInput(
        String[] paths
    ) {}

    public record PressKeyInput(
        String key
    ) {}

    public record NavigateBackInput() {}

    public record NavigateForwardInput() {}

    public record NetworkRequestsInput() {}

    public record PdfSaveInput(
        String filename
    ) {}

    public record DragInput(
        String startElement,
        String startRef,
        String endElement,
        String endRef
    ) {}

    public record HoverInput(
        String element,
        String ref
    ) {}

    public record SelectOptionInput(
        String element,
        String ref,
        String[] values
    ) {}
}
