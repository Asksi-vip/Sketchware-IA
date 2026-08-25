package pro.sketchware.activities.importicon.adapters;


import android.content.Context;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;

import pro.sketchware.databinding.ImportIconListItemBinding;
import pro.sketchware.utility.SvgUtils;

public class IconAdapter extends ListAdapter<Pair<String, String>, IconAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<Pair<String, String>> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Pair<String, String> oldItem, @NonNull Pair<String, String> newItem) {
            return oldItem.first.equals(newItem.first) && oldItem.second.equals(newItem.second);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Pair<String, String> oldItem, @NonNull Pair<String, String> newItem) {
            return true;
        }
    };

    private final SvgUtils svgUtils;
    private final OnIconSelectedListener listener;
    private String selected_icon_type;
    private int selected_color;

    public IconAdapter(Context context, String selected_icon_type, int selected_color, OnIconSelectedListener listener) {
        super(DIFF_CALLBACK);
        svgUtils = new SvgUtils(context);
        this.selected_icon_type = selected_icon_type;
        this.selected_color = selected_color;
        this.listener = listener;
    }

    public void setSelectedIconType(String selected_icon_type) {
        this.selected_icon_type = selected_icon_type;
    }

    public void setSelectedColor(int selected_color) {
        this.selected_color = selected_color;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String filePath = resolveIconFilePath(getItem(position));
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e("IconAdapter", "Icon file missing: " + filePath);
            holder.itemBinding.img.setImageDrawable(null);
            holder.itemBinding.title.setText(getItem(position).first);
            return;
        }

        try {
            if (filePath.endsWith(".svg")) {
                svgUtils.loadImage(holder.itemBinding.img, filePath);
            } else {
                holder.itemBinding.img.setImageURI(Uri.fromFile(file));
            }
        } catch (Exception e) {
            Log.e("IconAdapter", "Failed to bind icon: " + filePath, e);
        }
        holder.itemBinding.img.setColorFilter(selected_color, PorterDuff.Mode.SRC_IN);
        holder.itemBinding.title.setText(getItem(position).first);
    }

    private String resolveIconFilePath(Pair<String, String> icon) {
        File path = new File(icon.second);
        if (path.isDirectory()) {
            return new File(path, selected_icon_type + ".svg").getAbsolutePath();
        }
        return path.getAbsolutePath();
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImportIconListItemBinding binding = ImportIconListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    public interface OnIconSelectedListener {
        void onIconSelected(Pair<String, String> icon, int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ImportIconListItemBinding itemBinding;

        public ViewHolder(ImportIconListItemBinding binding) {
            super(binding.getRoot());
            itemBinding = binding;
            binding.getRoot().setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onIconSelected(getItem(position), position);
                }
            });
        }
    }
}

