package itkach.aard2.dictionaries;

import android.app.Application;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import itkach.aard2.descriptor.BlobDescriptor;
import itkach.aard2.BlobDescriptorList;
import itkach.aard2.descriptor.SlobDescriptor;
import itkach.aard2.SlobDescriptorList;
import itkach.aard2.SlobHelper;
import itkach.aard2.utils.ThreadUtils;

public class DictionaryListViewModel extends AndroidViewModel {
    @Nullable
    private SlobDescriptor dictionaryToBeReplaced;

    public DictionaryListViewModel(@NonNull Application application) {
        super(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    public void addDictionaries(@NonNull Intent intent) {
        ThreadUtils.postOnBackgroundThread(() -> {
            List<Uri> selection = new ArrayList<>();
            Uri dataUri = intent.getData();
            if (dataUri != null) {
                selection.add(dataUri);
            }
            ClipData clipData = intent.getClipData();
            if (clipData != null) {
                int itemCount = clipData.getItemCount();
                for (int i = 0; i < itemCount; i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    selection.add(uri);
                }
            }
            for (Uri uri : selection) {
                getApplication().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                SlobDescriptor sd = SlobDescriptor.fromUri(getApplication(), uri);
                SlobDescriptorList dictionaries = SlobHelper.getInstance().dictionaries;
                if (!dictionaries.hasId(sd.id)) {
                    dictionaries.add(sd);
                }
            }
        });
    }

    public void setDictionaryToBeReplaced(@Nullable SlobDescriptor dictionaryToBeReplaced) {
        this.dictionaryToBeReplaced = dictionaryToBeReplaced;
    }

    public void updateDictionary(@NonNull Uri newUri) {
        ThreadUtils.postOnBackgroundThread(() -> {
            if (dictionaryToBeReplaced != null) {
                getApplication().getContentResolver().takePersistableUriPermission(newUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                SlobHelper slobHelper = SlobHelper.getInstance();
                SlobDescriptorList dictionaries = slobHelper.dictionaries;
                SlobDescriptor newSd = SlobDescriptor.fromUri(getApplication(), newUri);
                if (!dictionaries.hasId(dictionaryToBeReplaced.id)) {
                    // Dictionary to be replaced does not exist for some reason
                    if (!dictionaries.hasId(newSd.id)) {
                        // The added dictionary is new, so add it before we're finished
                        dictionaries.add(newSd);
                    }
                    return;
                }
                // Replace dictionary
                dictionaries.remove(dictionaryToBeReplaced);
                // Only add the dictionary if it's new
                if (!dictionaries.hasId(newSd.id)) {
                    dictionaries.add(newSd);
                }
                // Update history and bookmarks
                String oldId = dictionaryToBeReplaced.id;
                String newId = newSd.id;
                String newSlobUri = slobHelper.getSlobUri(newId);
                BlobDescriptorList history = slobHelper.history;
                for (BlobDescriptor d : history.getList()) {
                    if (Objects.equals(d.slobId, oldId)) {
                        d.slobId = newId;
                        d.slobUri = newSlobUri;
                    }
                }
                BlobDescriptorList bookmarks = slobHelper.bookmarks;
                for (BlobDescriptor d : bookmarks.getList()) {
                    if (Objects.equals(d.slobId, oldId)) {
                        d.slobId = newId;
                        d.slobUri = newSlobUri;
                    }
                }
                ThreadUtils.postOnMainThread(() -> {
                    history.notifyDataSetChanged();
                    bookmarks.notifyDataSetChanged();
                });
            }
        });
    }
}
