package pro.sketchware.activities.chat;

import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import pro.sketchware.R;

public final class KelivoToolsBottomSheet {

    public interface Callback {
        void onCamera();

        void onPhotos();

        void onUpload();

        void onInstructionInjection();

        void onContextManagement();
    }

    private KelivoToolsBottomSheet() {
    }

    public static void show(@NonNull ChatActivity activity, @NonNull Callback callback) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_kelivo_tools, null);
        dialog.setContentView(content);

        View camera = content.findViewById(R.id.tool_camera);
        View photos = content.findViewById(R.id.tool_photos);
        View upload = content.findViewById(R.id.tool_upload);
        View instruction = content.findViewById(R.id.tool_instruction);
        View contextMgmt = content.findViewById(R.id.tool_context);

        if (camera != null) {
            camera.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onCamera();
            });
        }
        if (photos != null) {
            photos.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onPhotos();
            });
        }
        if (upload != null) {
            upload.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onUpload();
            });
        }
        if (instruction != null) {
            instruction.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onInstructionInjection();
            });
        }
        if (contextMgmt != null) {
            contextMgmt.setOnClickListener(v -> {
                dialog.dismiss();
                callback.onContextManagement();
            });
        }

        dialog.show();
    }
}
