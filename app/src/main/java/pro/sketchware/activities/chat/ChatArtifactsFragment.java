package pro.sketchware.activities.chat;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.activities.chat.port.VoidPortDiffService;
import pro.sketchware.util.FileChangeTracker;

public class ChatArtifactsFragment extends Fragment {
    private static final String ARG_SC_ID = "sc_id";
    private String scId;
    private List<ChatMessage> messages;
    private LinearLayout container;

    public static ChatArtifactsFragment newInstance(String scId) {
        ChatArtifactsFragment fragment = new ChatArtifactsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SC_ID, scId);
        fragment.setArguments(args);
        return fragment;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
        refreshArtifacts();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        scId = args == null ? null : args.getString(ARG_SC_ID);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(color(R.color.chat_surface));
        container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(12), dp(12), dp(12), dp(18));
        scrollView.addView(container, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        refreshArtifacts();
        return scrollView;
    }

    @Override
    public void onDestroyView() {
        container = null;
        super.onDestroyView();
    }

    public void refreshArtifacts() {
        if (container == null || !isAdded()) {
            return;
        }
        container.removeAllViews();
        addSectionTitle(R.string.chat_artifacts_files_title);
        renderChangedFiles();
        addSectionTitle(R.string.chat_artifacts_images_title);
        renderImages();
        addSectionTitle(R.string.chat_artifacts_tools_title);
        renderToolSummary();
    }

    private void renderChangedFiles() {
        Map<String, FileChangeTracker.FileChange> allChanges = FileChangeTracker.getAllRecentChanges(scId);
        List<FileChangeTracker.FileChange> changes = new ArrayList<>(allChanges.values());
        changes.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        if (changes.isEmpty()) {
            addTextCard(getString(R.string.chat_artifacts_no_files), "");
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (int i = 0; i < changes.size() && i < 12; i++) {
            FileChangeTracker.FileChange change = changes.get(i);
            VoidPortDiffService.DiffStats stats =
                    VoidPortDiffService.stats(change.beforeContent, change.afterContent);
            String detail = getString(R.string.chat_artifacts_file_detail,
                    stats.added,
                    stats.removed,
                    format.format(new Date(change.timestamp)));
            addTextCard(change.filePath, detail);
        }
    }

    private void renderImages() {
        List<ChatReference> images = new ArrayList<>();
        if (messages != null) {
            for (ChatMessage message : messages) {
                if (message != null && message.isUser()) {
                    images.addAll(message.getImageReferences());
                }
            }
        }
        if (images.isEmpty()) {
            addTextCard(getString(R.string.chat_artifacts_no_images), "");
            return;
        }
        HorizontalScrollView scrollView = new HorizontalScrollView(requireContext());
        scrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (ChatReference imageReference : images) {
            row.addView(makeImageArtifact(imageReference));
        }
        scrollView.addView(row);
        container.addView(scrollView, fullWidthParams(dp(10)));
    }

    private View makeImageArtifact(ChatReference reference) {
        Context context = requireContext();
        FrameLayout frame = new FrameLayout(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(104), dp(104));
        params.setMargins(0, 0, dp(10), 0);
        frame.setLayoutParams(params);
        frame.setPadding(dp(2), dp(2), dp(2), dp(2));
        frame.setBackground(rounded(color(R.color.chat_diff_background), color(R.color.chat_border)));

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            if (reference != null && reference.getUri() != null) {
                image.setImageURI(reference.getUri());
            } else {
                image.setImageResource(R.drawable.kelivo_lucide_image);
            }
        } catch (Exception ignored) {
            image.setImageResource(R.drawable.kelivo_lucide_image);
        }
        frame.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        return frame;
    }

    private void renderToolSummary() {
        ChatToolActivitySummary.Summary summary = ChatToolActivitySummary.summarize(messages);
        addTextCard(getString(R.string.chat_artifacts_tool_summary), summary.compactLabel());
        if (messages == null || summary.total() == 0) {
            return;
        }
        int rendered = 0;
        for (int i = messages.size() - 1; i >= 0 && rendered < 10; i--) {
            ChatMessage message = messages.get(i);
            if (message == null || !message.isTool()) {
                continue;
            }
            String name = ChatMessage.hasVisibleText(message.getToolName())
                    ? message.getToolName()
                    : getString(R.string.chat_tool_unknown);
            String status = ChatMessage.hasVisibleText(message.getStatus())
                    ? message.getStatus()
                    : (message.isToolError() ? getString(R.string.chat_tool_status_error) : getString(R.string.chat_tool_status_done));
            addTextCard(ChatToolActivitySummary.groupLabel(name) + " - " + name, status);
            rendered++;
        }
    }

    private void addSectionTitle(int stringRes) {
        TextView title = new TextView(requireContext());
        title.setText(stringRes);
        title.setTextColor(color(R.color.chat_text_primary));
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(10), 0, dp(6));
        container.addView(title);
    }

    private void addTextCard(String titleText, String detailText) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(color(R.color.chat_diff_background), color(R.color.chat_border)));
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = new TextView(requireContext());
        title.setText(titleText);
        title.setTextColor(color(R.color.chat_text_primary));
        title.setTextSize(13f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        card.addView(title);

        if (ChatMessage.hasVisibleText(detailText)) {
            TextView detail = new TextView(requireContext());
            detail.setText(detailText);
            detail.setTextColor(color(R.color.chat_text_secondary));
            detail.setTextSize(12f);
            detail.setPadding(0, dp(4), 0, 0);
            card.addView(detail);
        }
        container.addView(card, fullWidthParams(dp(8)));
    }

    private LinearLayout.LayoutParams fullWidthParams(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, bottomMargin);
        return params;
    }

    private GradientDrawable rounded(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int color(int resId) {
        return ContextCompat.getColor(requireContext(), resId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
