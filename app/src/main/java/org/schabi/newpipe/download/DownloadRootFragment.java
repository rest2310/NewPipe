package org.schabi.newpipe.download;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;

import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.ui.fragment.MissionsFragment;

/**
 * Root fragment used when the downloads screen is embedded inside {@code MainActivity}.
 *
 * <p>{@link MissionsFragment} expects the hosting screen to own {@code R.menu.download_menu},
 * while {@link DownloadActivity} also starts {@link DownloadManagerService} before embedding it.
 * This wrapper provides both pieces without launching a separate activity for bottom
 * navigation.</p>
 */
public class DownloadRootFragment extends Fragment {
    private static final String MISSIONS_FRAGMENT_TAG = "missions_fragment";

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        requireContext().startService(new Intent(requireContext(), DownloadManagerService.class));
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_downloads, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getChildFragmentManager().findFragmentByTag(MISSIONS_FRAGMENT_TAG) == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.download_fragment_holder, new MissionsFragment(),
                            MISSIONS_FRAGMENT_TAG)
                    .commit();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.downloads_title);
        requireActivity().invalidateOptionsMenu();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.download_menu, menu);
    }
}
