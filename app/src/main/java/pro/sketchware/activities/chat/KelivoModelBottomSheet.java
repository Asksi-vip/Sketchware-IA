package pro.sketchware.activities.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.activities.chat.port.VoidPortSettings;

public final class KelivoModelBottomSheet {

    public interface Callback {
        void onModelSelected(String providerId, String modelId);
    }

    private static final String PREF_PINNED = "pinned_models_v1";
    private static final String PREF_PINNED_LEGACY = "kelivo_pinned_models";

    private KelivoModelBottomSheet() {
    }

    public static void show(@NonNull ChatActivity activity, @NonNull Callback callback) {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(activity);
        List<VoidPortSettings.ProviderGroup> groups = new ArrayList<>();
        for (VoidPortSettings.ProviderGroup group : VoidPortSettings.getAllProviderGroups(prefs)) {
            if (!VoidPortSettings.isProviderSupportedInChat(group.providerId)) {
                continue;
            }
            if (!VoidPortSettings.isProviderConfigured(prefs, group.providerId)) {
                continue;
            }
            if (group.models.isEmpty()) {
                continue;
            }
            groups.add(group);
        }

        if (groups.isEmpty()) {
            android.widget.Toast.makeText(activity, R.string.chat_no_models_available, android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_kelivo_model, null);
        dialog.setContentView(content);

        String currentProvider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        String currentModel = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");

        EditText search = content.findViewById(R.id.model_search);
        ImageView favoritesJump = content.findViewById(R.id.model_favorites_jump);
        RecyclerView list = content.findViewById(R.id.model_list);
        LinearLayout chips = content.findViewById(R.id.provider_chips);

        List<VoidPortSettings.ProviderGroup> sourceGroups = groups;
        KelivoModelSheetAdapter adapter = new KelivoModelSheetAdapter();
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setAdapter(adapter);

        Runnable refresh = () -> {
            String query = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(Locale.getDefault());
            adapter.submit(buildRows(activity, sourceGroups, query, currentProvider, currentModel));
            if (favoritesJump != null) {
                favoritesJump.setVisibility(getPinned(activity).isEmpty() ? View.GONE : View.VISIBLE);
            }
        };

        adapter.setListener(new KelivoModelSheetAdapter.Listener() {
            @Override
            public void onModelSelected(String providerId, String modelId) {
                prefs.edit()
                        .putString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, providerId)
                        .putString(AiChatSettingsHelper.PREF_CURRENT_MODEL, modelId)
                        .apply();
                callback.onModelSelected(providerId, modelId);
                dialog.dismiss();
            }

            @Override
            public void onFavoriteToggle(String providerId, String modelId) {
                togglePinned(activity, providerId, modelId);
                refresh.run();
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        if (favoritesJump != null) {
            favoritesJump.setOnClickListener(v -> {
                if (search.getText() != null && search.getText().length() > 0) {
                    search.setText("");
                }
                list.post(() -> {
                    int position = adapter.findProviderSectionPosition("favorites");
                    if (position >= 0) {
                        list.smoothScrollToPosition(position);
                    }
                });
            });
        }

        buildProviderChips(activity, chips, sourceGroups, currentProvider, providerId -> {
            int position = adapter.findProviderSectionPosition(providerId);
            if (position >= 0) {
                list.scrollToPosition(position);
            }
        });

        refresh.run();

        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
                int targetHeight = (int) (metrics.heightPixels * 0.82f);
                sheet.getLayoutParams().height = targetHeight;
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setPeekHeight(targetHeight);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        dialog.show();
    }

    private static List<KelivoModelSheetAdapter.Row> buildRows(
            Context context,
            List<VoidPortSettings.ProviderGroup> groups,
            String query,
            String currentProvider,
            String currentModel) {
        List<KelivoModelSheetAdapter.Row> rows = new ArrayList<>();
        Set<String> pinned = getPinned(context);
        List<KelivoModelSheetAdapter.Row> favoriteRows = new ArrayList<>();
        for (VoidPortSettings.ProviderGroup group : groups) {
            for (String model : group.models) {
                if (!matchesQuery(group, model, query)) {
                    continue;
                }
                if (!pinned.contains(pinnedKey(group.providerId, model))) {
                    continue;
                }
                boolean selected = group.providerId.equals(currentProvider) && model.equals(currentModel);
                favoriteRows.add(new KelivoModelSheetAdapter.Row(
                        group.providerId, group.label, model, selected, true));
            }
        }
        if (!favoriteRows.isEmpty()) {
            rows.add(new KelivoModelSheetAdapter.Row("favorites", context.getString(R.string.kelivo_model_favorites)));
            rows.addAll(favoriteRows);
        }
        for (VoidPortSettings.ProviderGroup group : groups) {
            List<String> models = new ArrayList<>();
            for (String model : group.models) {
                if (matchesQuery(group, model, query)) {
                    models.add(model);
                }
            }
            if (models.isEmpty()) {
                continue;
            }
            rows.add(new KelivoModelSheetAdapter.Row(group.providerId, group.label));
            for (String model : models) {
                boolean selected = group.providerId.equals(currentProvider) && model.equals(currentModel);
                boolean modelPinned = pinned.contains(pinnedKey(group.providerId, model));
                rows.add(new KelivoModelSheetAdapter.Row(group.providerId, group.label, model, selected, modelPinned));
            }
        }
        return rows;
    }

    private static boolean matchesQuery(VoidPortSettings.ProviderGroup group, String model, String query) {
        return query == null
                || query.isEmpty()
                || model.toLowerCase(Locale.getDefault()).contains(query)
                || group.label.toLowerCase(Locale.getDefault()).contains(query)
                || group.providerId.toLowerCase(Locale.getDefault()).contains(query);
    }

    private static void buildProviderChips(
            Context context,
            LinearLayout container,
            List<VoidPortSettings.ProviderGroup> groups,
            String selectedProviderId,
            ChipClickListener listener) {
        container.removeAllViews();
        int pad = dp(context, 6);
        for (VoidPortSettings.ProviderGroup group : groups) {
            LinearLayout chip = new LinearLayout(context);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setPadding(dp(context, 12), dp(context, 8), dp(context, 14), dp(context, 8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(pad);
            chip.setLayoutParams(params);

            int iconRes = KelivoModelIconResolver.resolveProvider(group.providerId, group.label);
            if (iconRes != 0) {
                ImageView icon = new ImageView(context);
                icon.setImageResource(iconRes);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18));
                iconParams.setMarginEnd(dp(context, 7));
                chip.addView(icon, iconParams);
            }

            TextView label = new TextView(context);
            label.setText(group.label);
            label.setTextSize(13f);
            boolean selected = group.providerId.equals(selectedProviderId);
            chip.setBackgroundResource(selected
                    ? R.drawable.bg_kelivo_provider_chip_selected
                    : R.drawable.bg_kelivo_provider_chip);
            label.setTextColor(context.getColor(selected
                    ? R.color.chat_accent
                    : R.color.chat_text_primary));
            chip.addView(label);
            chip.setOnClickListener(v -> listener.onChipClick(group.providerId));
            container.addView(chip);
        }
    }

    private static Set<String> getPinned(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE);
        Set<String> pinned = new HashSet<>(prefs.getStringSet(PREF_PINNED, new HashSet<>()));
        pinned.addAll(prefs.getStringSet(PREF_PINNED_LEGACY, new HashSet<>()));
        return pinned;
    }

    private static void togglePinned(Context context, String providerId, String modelId) {
        SharedPreferences prefs = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE);
        Set<String> pinned = new HashSet<>(prefs.getStringSet(PREF_PINNED, new HashSet<>()));
        Set<String> legacyPinned = new HashSet<>(prefs.getStringSet(PREF_PINNED_LEGACY, new HashSet<>()));
        pinned.addAll(legacyPinned);
        String key = pinnedKey(providerId, modelId);
        if (pinned.contains(key)) {
            pinned.remove(key);
            legacyPinned.remove(key);
        } else {
            pinned.add(key);
        }
        prefs.edit()
                .putStringSet(PREF_PINNED, pinned)
                .putStringSet(PREF_PINNED_LEGACY, legacyPinned)
                .apply();
    }

    private static String pinnedKey(String providerId, String modelId) {
        return (providerId == null ? "" : providerId) + "::" + (modelId == null ? "" : modelId);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private interface ChipClickListener {
        void onChipClick(String providerId);
    }
}
