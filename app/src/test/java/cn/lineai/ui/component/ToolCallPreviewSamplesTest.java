package cn.lineai.ui.component;

import cn.lineai.tool.ToolDisplayCategory;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class ToolCallPreviewSamplesTest {

    @Test
    public void everyCategoryHasValidSample() throws Exception {
        for (ToolDisplayCategory category : ToolDisplayCategory.values()) {
            ToolCallPreviewSamples.Sample sample = ToolCallPreviewSamples.forCategory(category);
            Assert.assertNotNull("missing sample for " + category, sample);
            Assert.assertNotNull(sample.call.getId());
            Assert.assertFalse(sample.call.getId().isEmpty());
            Assert.assertNotNull(sample.call.getName());
            Assert.assertFalse(sample.call.getName().isEmpty());
            // arguments 必须是合法 JSON
            new JSONObject(sample.call.getArguments());
        }
    }

    @Test
    public void writeSampleIsPendingReview() {
        ToolCallPreviewSamples.Sample sample = ToolCallPreviewSamples.forCategory(ToolDisplayCategory.WRITE);
        Assert.assertEquals("pending", sample.result.getReviewState());
        Assert.assertEquals("preview_diff", sample.result.getDiffId());
    }

    @Test
    public void runningShellHasNullResult() {
        ToolCallPreviewSamples.Sample sample = ToolCallPreviewSamples.runningShell();
        Assert.assertNull(sample.result);
        Assert.assertEquals("shell_execute", sample.call.getName());
    }
}
