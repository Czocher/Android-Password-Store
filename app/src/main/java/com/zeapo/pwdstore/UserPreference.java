package com.zeapo.pwdstore;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.nononsenseapps.filepicker.FilePickerActivity;
import com.zeapo.pwdstore.autofill.AutofillPreferenceActivity;
import com.zeapo.pwdstore.crypto.PgpHandler;
import com.zeapo.pwdstore.git.GitActivity;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserPreference extends AppCompatActivity {
    private final static int IMPORT_SSH_KEY = 1;
    private final static int IMPORT_PGP_KEY = 2;
    private final static int EDIT_GIT_INFO = 3;
    private final static int SELECT_GIT_DIRECTORY = 4;

    public static class PrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final UserPreference callingActivity = (UserPreference) getActivity();
            final SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();

            addPreferencesFromResource(R.xml.preference);

            findPreference("ssh_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    callingActivity.getSshKey();
                    return true;
                }
            });

            findPreference("ssh_keygen").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    callingActivity.makeSshKey(true);
                    return true;
                }
            });

            findPreference("ssh_see_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    DialogFragment df = new SshKeyGen.ShowSshKeyFragment();
                    df.show(getFragmentManager(), "public_key");
                    return true;
                }
            });


            findPreference("git_server_info").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(callingActivity, GitActivity.class);
                    intent.putExtra("Operation", GitActivity.EDIT_SERVER);
                    startActivityForResult(intent, EDIT_GIT_INFO);
                    return true;
                }
            });

            findPreference("git_delete_repo").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(callingActivity).
                            setTitle(R.string.pref_dialog_delete_title).
                            setMessage(getResources().getString(R.string.dialog_delete_msg)
                                    + " \n" + PasswordRepository.getWorkTree().toString()).
                            setCancelable(false).
                            setPositiveButton(R.string.dialog_delete, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    try {
                                        FileUtils.cleanDirectory(PasswordRepository.getWorkTree());
                                        PasswordRepository.closeRepository();
                                    } catch (Exception e) {
                                        //TODO Handle the diffent cases of exceptions
                                    }

                                    sharedPreferences.edit().putBoolean("repository_initialized", false).apply();
                                    dialogInterface.cancel();
                                    callingActivity.finish();
                                }
                            }).
                            setNegativeButton(R.string.dialog_do_not_delete,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            {
                                                dialogInterface.cancel();
                                            }
                                        }
                                    }).show();

                    return true;
                }
            });

            findPreference("openpgp_key_id_pref").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(callingActivity, PgpHandler.class);
                    intent.putExtra("Operation", "GET_KEY_ID");
                    startActivityForResult(intent, IMPORT_PGP_KEY);
                    return true;
                }
            });

            final Preference externalRepo = findPreference("pref_select_external");
            externalRepo.setSummary(getPreferenceManager().getSharedPreferences().getString("git_external_repo", callingActivity.getString(R.string.no_repo_selected)));
            externalRepo.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    callingActivity.selectExternalGitRepository();
                    return true;
                }
            });

            Preference.OnPreferenceChangeListener resetRepo = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    findPreference("git_delete_repo").setEnabled(!(Boolean) o);
                    PasswordRepository.closeRepository();
                    getPreferenceManager().getSharedPreferences().edit().putBoolean("repo_changed", true).apply();
                    return true;
                }
            };

            findPreference("pref_select_external").setOnPreferenceChangeListener(resetRepo);
            findPreference("git_external").setOnPreferenceChangeListener(resetRepo);

            findPreference("autofill_apps").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(callingActivity, AutofillPreferenceActivity.class);
                    startActivity(intent);
                    return true;
                }
            });

            findPreference("autofill_enable").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(callingActivity).
                            setTitle(R.string.pref_autofill_enable_title).
                            setView(R.layout.autofill_instructions).
                            setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                    startActivity(intent);
                                }
                            }).
                            setNegativeButton(R.string.dialog_cancel, null).
                            setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    ((CheckBoxPreference) findPreference("autofill_enable"))
                                            .setChecked(((UserPreference) getActivity()).isServiceEnabled());
                                }
                            }).show();
                    return true;
                }
            });
        }

        @Override
        public void onStart() {
            super.onStart();
            final SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
            findPreference("pref_select_external").setSummary(getPreferenceManager().getSharedPreferences().getString("git_external_repo", getString(R.string.no_repo_selected)));
            findPreference("ssh_see_key").setEnabled(sharedPreferences.getBoolean("use_generated_key", false));
            findPreference("git_delete_repo").setEnabled(!sharedPreferences.getBoolean("git_external", false));

            Preference keyPref = findPreference("openpgp_key_id_pref");
            Set<String> selectedKeys = sharedPreferences.getStringSet("openpgp_key_ids_set", new HashSet<String>());
            if (selectedKeys.isEmpty()) {
                keyPref.setSummary("No key selected");
            } else {
                StringBuilder summaryBuilder = new StringBuilder();
                for (String s: selectedKeys) {
                    summaryBuilder.append(Long.valueOf(s)).append(" ");
                }
                keyPref.setSummary(summaryBuilder.toString());
            }

            // see if the autofill service is enabled and check the preference accordingly
            ((CheckBoxPreference) findPreference("autofill_enable"))
                    .setChecked(((UserPreference) getActivity()).isServiceEnabled());
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null) {
            if (getIntent().getStringExtra("operation") != null) {
                switch (getIntent().getStringExtra("operation")) {
                    case "get_ssh_key":
                        getSshKey();
                        break;
                    case "make_ssh_key":
                        makeSshKey(false);
                        break;
                    case "git_external":
                        selectExternalGitRepository();
                        break;
                }
            }
        }

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefsFragment()).commit();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public void selectExternalGitRepository() {
        final Activity activity = this;
        new AlertDialog.Builder(this).
                setTitle("Choose where to store the passwords").
                setMessage("You must select a directory where to store your passwords. If you want " +
                        "to store your passwords within the hidden storage of the application, " +
                        "cancel this dialog and disable the \"External Repository\" option.").
                setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    // This always works
                    Intent i = new Intent(activity.getApplicationContext(), FilePickerActivity.class);
                    // This works if you defined the intent filter
                    // Intent i = new Intent(Intent.ACTION_GET_CONTENT);

                    // Set these depending on your use case. These are the defaults.
                    i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
                    i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
                    i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR);

                    i.putExtra(FilePickerActivity.EXTRA_START_PATH, Environment.getExternalStorageDirectory().getPath());

                    startActivityForResult(i, SELECT_GIT_DIRECTORY);
                    }
                }).
                setNegativeButton(R.string.dialog_cancel, null).show();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                setResult(RESULT_OK);
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Opens a file explorer to import the private key
     */
    public void getSshKey() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_SSH_KEY);
    }

    /**
     * Opens a key generator to generate a public/private key pair
     */
    public void makeSshKey(boolean fromPreferences) {
        Intent intent = new Intent(getApplicationContext(), SshKeyGen.class);
        startActivity(intent);
        if (!fromPreferences) {
            setResult(RESULT_OK);
            finish();
        }
    }

    private void copySshKey(Uri uri) throws IOException {
        InputStream sshKey = this.getContentResolver().openInputStream(uri);
        byte[] privateKey = IOUtils.toByteArray(sshKey);
        FileUtils.writeByteArrayToFile(new File(getFilesDir() + "/.ssh_key"), privateKey);
        sshKey.close();
    }

    // Returns whether the autofill service is enabled
    private boolean isServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) this
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> runningServices = am
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo service : runningServices) {
            if ("com.zeapo.pwdstore/.autofill.AutofillService".equals(service.getId())) {
                return true;
            }
        }
        return false;
    }


    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case IMPORT_SSH_KEY: {
                    try {
                        if (data.getData() == null) {
                            throw new IOException("Unable to open file");
                        }
                        copySshKey(data.getData());
                        Toast.makeText(this, this.getResources().getString(R.string.ssh_key_success_dialog_title), Toast.LENGTH_LONG).show();
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("use_generated_key", false);
                        editor.apply();

                        //delete the public key from generation
                        File file = new File(getFilesDir() + "/.ssh_key.pub");
                        file.delete();

                        setResult(RESULT_OK);
                        finish();
                    } catch (IOException e) {
                        new AlertDialog.Builder(this).
                                setTitle(this.getResources().getString(R.string.ssh_key_error_dialog_title)).
                                setMessage(this.getResources().getString(R.string.ssh_key_error_dialog_text) + e.getMessage()).
                                setPositiveButton(this.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        // pass
                                    }
                                }).show();
                    }
                }
                break;
                case EDIT_GIT_INFO: {

                }
                break;
                case SELECT_GIT_DIRECTORY: {
                    final Uri uri = data.getData();

                    if (uri.getPath().equals(Environment.getExternalStorageDirectory().getPath())) {
                        // the user wants to use the root of the sdcard as a store...
                        new AlertDialog.Builder(this).
                                setTitle("SD-Card root selected").
                                setMessage("You have selected the root of your sdcard for the store. " +
                                        "This is extremely dangerous and you will lose your data " +
                                        "as its content will, eventually, be deleted").
                                setPositiveButton("Remove everything", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                                                .edit()
                                                .putString("git_external_repo", uri.getPath())
                                                .apply();
                                    }
                                }).
                                setNegativeButton(R.string.dialog_cancel, null).show();
                    } else {
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                                .edit()
                                .putString("git_external_repo", uri.getPath())
                                .apply();
                    }
                }
                break;
                default:
                    break;
            }
        }
    }
}
