package com.sample.android.contact.ui;

import android.Manifest;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.sample.android.contact.BR;
import com.sample.android.contact.R;
import com.sample.android.contact.databinding.FragmentContactsBinding;
import com.sample.android.contact.domain.Contact;
import com.sample.android.contact.util.Resource;
import com.sample.android.contact.viewmodels.ContactsViewModel;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import dagger.android.support.DaggerFragment;

import static com.sample.android.contact.ContactsServiceKt.CONTACTS_RECEIVER;

public class ContactsFragment extends DaggerFragment {

    public static final String CONTACTS = "contacts";

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            List<Contact> contacts = intent.getParcelableArrayListExtra(CONTACTS);
            mContacts = contacts;
            mAdapter.setItems(contacts, true);
            mProgressBar.setVisibility(View.GONE);
            mAppBarLayout.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    };

    @Inject
    ContactsViewModel.Factory factory;

    private ContactsViewModel mViewModel;

    // Request code for READ_CONTACTS. It can be any number > 0.
    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;

    public static final String[] PROJECTION = new String[]{
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
    };
    private ContactsAdapter mAdapter;

    @BindView(R.id.recyclerView)
    RecyclerView mRecyclerView;

    @BindView(R.id.search_view)
    SearchView mSearchView;

    @BindView(R.id.search_back)
    ImageButton mSearchBack;

    @BindView(R.id.appBarLayout)
    View mAppBarLayout;

    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;

    private Unbinder unbinder;

    private List<Contact> mContacts;

    @Inject
    public ContactsFragment() {
        // Requires empty public constructor
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(CONTACTS_RECEIVER));
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(broadcastReceiver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_contacts, container, false);
        unbinder = ButterKnife.bind(this, root);

        mViewModel = new ViewModelProvider(this, factory).get(ContactsViewModel.class);
        ViewDataBinding binding = FragmentContactsBinding.bind(root);
        binding.setVariable(BR.vm, mViewModel);
        binding.setLifecycleOwner(getViewLifecycleOwner());

        mAdapter = new ContactsAdapter();
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setHasFixedSize(true);

        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
        // hint, inputType & ime options seem to be ignored from XML! Set in code
        mSearchView.setQueryHint(getString(R.string.search_hint));
        mSearchView.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                search(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                if (!query.isEmpty()) {
                    mSearchBack.setVisibility(View.VISIBLE);
                    search(query);
                }
                return true;
            }
        });

        int searchCloseIconButtonId = getResources().getIdentifier("android:id/search_close_btn", null, null);
        ImageView searchClose = mSearchView.findViewById(searchCloseIconButtonId);
        int searchCloseIconColor = ResourcesCompat.getColor(getResources(), R.color.color3, null);
        searchClose.setColorFilter(searchCloseIconColor);

        mSearchBack.setOnClickListener(view -> {
            mAdapter.setItems(mContacts, true);
            mSearchBack.setVisibility(View.INVISIBLE);
            mSearchView.setQuery("", false);
        });

        // Check the SDK version and whether the permission is already granted or not.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && getActivity().checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSIONS_REQUEST_READ_CONTACTS);
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method
        } else {
            // Android version is lesser than 6.0 or the permission is already granted.
        }

        // Create the observer which updates the UI.
        final Observer<Resource<List<Contact>>> contactsObserver = resource -> {
            if (resource instanceof Resource.Success) {
                List<Contact> items = ((Resource.Success<List<Contact>>) resource).getData();
                mAdapter.setItems(items, false);
            }
        };

        // Observe the LiveData, passing in this fragment as the LifecycleOwner and the observer.
        mViewModel.getLiveData().observe(this, contactsObserver);

        return root;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
            } else {
                mAppBarLayout.setVisibility(View.GONE);
                Toast.makeText(getActivity(), "Until you grant the permission, we canot display the names", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    private void search(String query) {
        final String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ? OR " +
                ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?";
        final String[] selectionArgs = new String[]{"%" + query + "%", "%" + query + "%"};
        mViewModel.showContacts(selection, selectionArgs);
    }
}
