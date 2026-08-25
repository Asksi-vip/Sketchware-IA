package pro.sketchware.utility;

import android.content.Context;
import android.content.res.Resources;

import java.util.Locale;

public class TranslationResources extends Resources {

    private final Context applicationContext;
    private final Resources baseResources;

    public TranslationResources(Context context, Resources baseResources) {
        super(baseResources.getAssets(), baseResources.getDisplayMetrics(), baseResources.getConfiguration());
        this.applicationContext = context.getApplicationContext();
        this.baseResources = baseResources;
    }

    public Resources getBaseResources() {
        return baseResources;
    }

    @Override
    public CharSequence getText(int id) throws NotFoundException {
        String override = TranslationFunction.getOverrideString(applicationContext, baseResources, id);
        return override != null ? override : baseResources.getText(id);
    }

    @Override
    public CharSequence getText(int id, CharSequence def) {
        String override = TranslationFunction.getOverrideString(applicationContext, baseResources, id);
        return override != null ? override : baseResources.getText(id, def);
    }

    @Override
    public String getString(int id) throws NotFoundException {
        String override = TranslationFunction.getOverrideString(applicationContext, baseResources, id);
        return override != null ? override : baseResources.getString(id);
    }

    @Override
    public String getString(int id, Object... formatArgs) throws NotFoundException {
        String override = TranslationFunction.getOverrideString(applicationContext, baseResources, id);
        if (override != null) {
            return TranslationFunction.formatString(Locale.getDefault(), override, formatArgs);
        }
        return baseResources.getString(id, formatArgs);
    }
}
