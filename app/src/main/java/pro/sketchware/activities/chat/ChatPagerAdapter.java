package pro.sketchware.activities.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import pro.sketchware.R;

public class ChatPagerAdapter extends FragmentStatePagerAdapter {
    private final ChatActivity activity;
    private final ChatMessagesFragment messagesFragment;
    private final ChatDiffFragment diffFragment;
    private final ChatArtifactsFragment artifactsFragment;
    private final ChatPlanFragment planFragment;

    public ChatPagerAdapter(@NonNull ChatActivity activity,
                            @NonNull ChatMessagesFragment messagesFragment,
                            @NonNull ChatDiffFragment diffFragment,
                            @NonNull ChatArtifactsFragment artifactsFragment,
                            @NonNull ChatPlanFragment planFragment) {
        super(activity.getSupportFragmentManager(), FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.activity = activity;
        this.messagesFragment = messagesFragment;
        this.diffFragment = diffFragment;
        this.artifactsFragment = artifactsFragment;
        this.planFragment = planFragment;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        return switch (position) {
            case 1 -> diffFragment;
            case 2 -> artifactsFragment;
            case 3 -> planFragment;
            default -> messagesFragment;
        };
    }

    @Override
    public int getCount() {
        return 4;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return switch (position) {
            case 1 -> activity.getString(R.string.chat_page_diffs);
            case 2 -> activity.getString(R.string.chat_page_artifacts);
            case 3 -> activity.getString(R.string.chat_page_plan);
            default -> activity.getString(R.string.chat_page_chat);
        };
    }
}
