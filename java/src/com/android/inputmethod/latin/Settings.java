/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.android.inputmethod.compat.CompatUtils;
import com.android.inputmethod.compat.InputMethodManagerCompatWrapper;
import com.android.inputmethod.compat.InputMethodServiceCompatWrapper;
import com.android.inputmethod.compat.VibratorCompatWrapper;
import com.android.inputmethod.deprecated.VoiceProxy;
import com.android.inputmethodcommon.InputMethodSettingsActivity;

import java.util.Locale;

public class Settings extends InputMethodSettingsActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        DialogInterface.OnDismissListener, OnPreferenceClickListener {
    private static final String TAG = Settings.class.getSimpleName();

    public static final boolean ENABLE_EXPERIMENTAL_SETTINGS = false;

    // In the same order as xml/prefs.xml
    public static final String PREF_GENERAL_SETTINGS = "general_settings";
    public static final String PREF_SUBTYPES_SETTINGS = "subtype_settings";
    public static final String PREF_AUTO_CAP = "auto_cap";
    public static final String PREF_VIBRATE_ON = "vibrate_on";
    public static final String PREF_SOUND_ON = "sound_on";
    public static final String PREF_POPUP_ON = "popup_on";
    public static final String PREF_VOICE_MODE = "voice_mode";
    public static final String PREF_CORRECTION_SETTINGS = "correction_settings";
    public static final String PREF_CONFIGURE_DICTIONARIES_KEY = "configure_dictionaries_key";
    public static final String PREF_AUTO_CORRECTION_THRESHOLD = "auto_correction_threshold";
    public static final String PREF_SHOW_SUGGESTIONS_SETTING = "show_suggestions_setting";
    public static final String PREF_MISC_SETTINGS = "misc_settings";
    public static final String PREF_USABILITY_STUDY_MODE = "usability_study_mode";
    public static final String PREF_ADVANCED_SETTINGS = "pref_advanced_settings";
    public static final String PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY =
            "pref_key_preview_popup_dismiss_delay";
    public static final String PREF_KEY_USE_CONTACTS_DICT = "pref_key_use_contacts_dict";
    public static final String PREF_BIGRAM_SUGGESTION = "bigram_suggestion";
    public static final String PREF_BIGRAM_PREDICTIONS = "bigram_prediction";
    public static final String PREF_KEY_ENABLE_SPAN_INSERT = "enable_span_insert";
    public static final String PREF_VIBRATION_DURATION_SETTINGS =
            "pref_vibration_duration_settings";
    public static final String PREF_KEYPRESS_SOUND_VOLUME =
            "pref_keypress_sound_volume";

    public static final String PREF_INPUT_LANGUAGE = "input_language";
    public static final String PREF_SELECTED_LANGUAGES = "selected_languages";
    public static final String PREF_DEBUG_SETTINGS = "debug_settings";

    // Dialog ids
    private static final int VOICE_INPUT_CONFIRM_DIALOG = 0;

    private PreferenceScreen mInputLanguageSelection;
    private PreferenceScreen mKeypressVibrationDurationSettingsPref;
    private PreferenceScreen mKeypressSoundVolumeSettingsPref;
    private ListPreference mVoicePreference;
    private ListPreference mShowCorrectionSuggestionsPreference;
    private ListPreference mAutoCorrectionThresholdPreference;
    private ListPreference mKeyPreviewPopupDismissDelay;
    // Suggestion: use bigrams to adjust scores of suggestions obtained from unigram dictionary
    private CheckBoxPreference mBigramSuggestion;
    // Prediction: use bigrams to predict the next word when there is no input for it yet
    private CheckBoxPreference mBigramPrediction;
    private Preference mDebugSettingsPreference;
    private boolean mVoiceOn;

    private AlertDialog mDialog;
    private TextView mKeypressVibrationDurationSettingsTextView;
    private TextView mKeypressSoundVolumeSettingsTextView;

    private boolean mOkClicked = false;
    private String mVoiceModeOff;

    private void ensureConsistencyOfAutoCorrectionSettings() {
        final String autoCorrectionOff = getResources().getString(
                R.string.auto_correction_threshold_mode_index_off);
        final String currentSetting = mAutoCorrectionThresholdPreference.getValue();
        mBigramSuggestion.setEnabled(!currentSetting.equals(autoCorrectionOff));
        if (null != mBigramPrediction) {
            mBigramPrediction.setEnabled(!currentSetting.equals(autoCorrectionOff));
        }
    }

    public Activity getActivityInternal() {
        Object thisObject = (Object) this;
        if (thisObject instanceof Activity) {
            return (Activity) thisObject;
        } else if (thisObject instanceof Fragment) {
            return ((Fragment) thisObject).getActivity();
        } else {
            return null;
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
        setSubtypeEnablerTitle(R.string.select_language);
        final Resources res = getResources();
        final Context context = getActivityInternal();

        addPreferencesFromResource(R.xml.prefs);
        mInputLanguageSelection = (PreferenceScreen) findPreference(PREF_SUBTYPES_SETTINGS);
        mInputLanguageSelection.setOnPreferenceClickListener(this);
        mVoicePreference = (ListPreference) findPreference(PREF_VOICE_MODE);
        mShowCorrectionSuggestionsPreference =
                (ListPreference) findPreference(PREF_SHOW_SUGGESTIONS_SETTING);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        mVoiceModeOff = getString(R.string.voice_mode_off);
        mVoiceOn = !(prefs.getString(PREF_VOICE_MODE, mVoiceModeOff)
                .equals(mVoiceModeOff));

        mAutoCorrectionThresholdPreference =
                (ListPreference) findPreference(PREF_AUTO_CORRECTION_THRESHOLD);
        mBigramSuggestion = (CheckBoxPreference) findPreference(PREF_BIGRAM_SUGGESTION);
        mBigramPrediction = (CheckBoxPreference) findPreference(PREF_BIGRAM_PREDICTIONS);
        mDebugSettingsPreference = findPreference(PREF_DEBUG_SETTINGS);
        if (mDebugSettingsPreference != null) {
            final Intent debugSettingsIntent = new Intent(Intent.ACTION_MAIN);
            debugSettingsIntent.setClassName(
                    context.getPackageName(), DebugSettings.class.getName());
            mDebugSettingsPreference.setIntent(debugSettingsIntent);
        }

        ensureConsistencyOfAutoCorrectionSettings();

        final PreferenceGroup generalSettings =
                (PreferenceGroup) findPreference(PREF_GENERAL_SETTINGS);
        final PreferenceGroup textCorrectionGroup =
                (PreferenceGroup) findPreference(PREF_CORRECTION_SETTINGS);
        final PreferenceGroup miscSettings =
                (PreferenceGroup) findPreference(PREF_MISC_SETTINGS);

        final boolean showVoiceKeyOption = res.getBoolean(
                R.bool.config_enable_show_voice_key_option);
        if (!showVoiceKeyOption) {
            generalSettings.removePreference(mVoicePreference);
        }

        if (!VibratorCompatWrapper.getInstance(context).hasVibrator()) {
            generalSettings.removePreference(findPreference(PREF_VIBRATE_ON));
        }

        if (InputMethodServiceCompatWrapper.CAN_HANDLE_ON_CURRENT_INPUT_METHOD_SUBTYPE_CHANGED) {
            generalSettings.removePreference(findPreference(PREF_SUBTYPES_SETTINGS));
        }

        final boolean showPopupOption = res.getBoolean(
                R.bool.config_enable_show_popup_on_keypress_option);
        if (!showPopupOption) {
            generalSettings.removePreference(findPreference(PREF_POPUP_ON));
        }

        final boolean showBigramSuggestionsOption = res.getBoolean(
                R.bool.config_enable_bigram_suggestions_option);
        if (!showBigramSuggestionsOption) {
            textCorrectionGroup.removePreference(mBigramSuggestion);
            if (null != mBigramPrediction) {
                textCorrectionGroup.removePreference(mBigramPrediction);
            }
        }

        mKeyPreviewPopupDismissDelay =
                (ListPreference)findPreference(PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
        final String[] entries = new String[] {
                res.getString(R.string.key_preview_popup_dismiss_no_delay),
                res.getString(R.string.key_preview_popup_dismiss_default_delay),
        };
        final String popupDismissDelayDefaultValue = Integer.toString(res.getInteger(
                R.integer.config_key_preview_linger_timeout));
        mKeyPreviewPopupDismissDelay.setEntries(entries);
        mKeyPreviewPopupDismissDelay.setEntryValues(
                new String[] { "0", popupDismissDelayDefaultValue });
        if (null == mKeyPreviewPopupDismissDelay.getValue()) {
            mKeyPreviewPopupDismissDelay.setValue(popupDismissDelayDefaultValue);
        }
        mKeyPreviewPopupDismissDelay.setEnabled(
                SettingsValues.isKeyPreviewPopupEnabled(prefs, res));

        final PreferenceScreen dictionaryLink =
                (PreferenceScreen) findPreference(PREF_CONFIGURE_DICTIONARIES_KEY);
        final Intent intent = dictionaryLink.getIntent();

        final int number = context.getPackageManager().queryIntentActivities(intent, 0).size();
        if (0 >= number) {
            textCorrectionGroup.removePreference(dictionaryLink);
        }

        final boolean isResearcherPackage = LatinImeLogger.isResearcherPackage(this);
        final boolean showUsabilityStudyModeOption =
                res.getBoolean(R.bool.config_enable_usability_study_mode_option)
                        || isResearcherPackage || ENABLE_EXPERIMENTAL_SETTINGS;
        final Preference usabilityStudyPref = findPreference(PREF_USABILITY_STUDY_MODE);
        if (!showUsabilityStudyModeOption) {
            if (usabilityStudyPref != null) {
                miscSettings.removePreference(usabilityStudyPref);
            }
        }
        if (isResearcherPackage) {
            if (usabilityStudyPref instanceof CheckBoxPreference) {
                CheckBoxPreference checkbox = (CheckBoxPreference)usabilityStudyPref;
                checkbox.setChecked(prefs.getBoolean(PREF_USABILITY_STUDY_MODE, true));
                checkbox.setSummary(R.string.settings_warning_researcher_mode);
            }
        }

        mKeypressVibrationDurationSettingsPref =
                (PreferenceScreen) findPreference(PREF_VIBRATION_DURATION_SETTINGS);
        if (mKeypressVibrationDurationSettingsPref != null) {
            mKeypressVibrationDurationSettingsPref.setOnPreferenceClickListener(
                    new OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference arg0) {
                            showKeypressVibrationDurationSettingsDialog();
                            return true;
                        }
                    });
            updateKeypressVibrationDurationSettingsSummary(prefs, res);
        }

        mKeypressSoundVolumeSettingsPref =
                (PreferenceScreen) findPreference(PREF_KEYPRESS_SOUND_VOLUME);
        if (mKeypressSoundVolumeSettingsPref != null) {
            mKeypressSoundVolumeSettingsPref.setOnPreferenceClickListener(
                    new OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference arg0) {
                            showKeypressSoundVolumeSettingDialog();
                            return true;
                        }
                    });
            updateKeypressSoundVolumeSummary(prefs, res);
        }
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, res);
    }

    @SuppressWarnings("unused")
    @Override
    public void onResume() {
        super.onResume();
        final boolean isShortcutImeEnabled = SubtypeSwitcher.getInstance().isShortcutImeEnabled();
        if (isShortcutImeEnabled
                || (VoiceProxy.VOICE_INSTALLED
                        && VoiceProxy.isRecognitionAvailable(getActivityInternal()))) {
            updateVoiceModeSummary();
        } else {
            getPreferenceScreen().removePreference(mVoicePreference);
        }
        updateShowCorrectionSuggestionsSummary();
        updateKeyPreviewPopupDelaySummary();
    }

    @Override
    public void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        (new BackupManager(getActivityInternal())).dataChanged();
        // If turning on voice input, show dialog
        if (key.equals(PREF_VOICE_MODE) && !mVoiceOn) {
            if (!prefs.getString(PREF_VOICE_MODE, mVoiceModeOff)
                    .equals(mVoiceModeOff)) {
                showVoiceConfirmation();
            }
        } else if (key.equals(PREF_POPUP_ON)) {
            final ListPreference popupDismissDelay =
                (ListPreference)findPreference(PREF_KEY_PREVIEW_POPUP_DISMISS_DELAY);
            if (null != popupDismissDelay) {
                popupDismissDelay.setEnabled(prefs.getBoolean(PREF_POPUP_ON, true));
            }
        }
        ensureConsistencyOfAutoCorrectionSettings();
        mVoiceOn = !(prefs.getString(PREF_VOICE_MODE, mVoiceModeOff)
                .equals(mVoiceModeOff));
        updateVoiceModeSummary();
        updateShowCorrectionSuggestionsSummary();
        updateKeyPreviewPopupDelaySummary();
        refreshEnablingsOfKeypressSoundAndVibrationSettings(prefs, getResources());
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        if (pref == mInputLanguageSelection) {
            final String imeId = Utils.getInputMethodId(
                    getActivityInternal().getApplicationInfo().packageName);
            startActivity(CompatUtils.getInputLanguageSelectionIntent(imeId, 0));
            return true;
        }
        return false;
    }

    private void updateShowCorrectionSuggestionsSummary() {
        mShowCorrectionSuggestionsPreference.setSummary(
                getResources().getStringArray(R.array.prefs_suggestion_visibilities)
                [mShowCorrectionSuggestionsPreference.findIndexOfValue(
                        mShowCorrectionSuggestionsPreference.getValue())]);
    }

    private void updateKeyPreviewPopupDelaySummary() {
        final ListPreference lp = mKeyPreviewPopupDismissDelay;
        lp.setSummary(lp.getEntries()[lp.findIndexOfValue(lp.getValue())]);
    }

    private void showVoiceConfirmation() {
        mOkClicked = false;
        getActivityInternal().showDialog(VOICE_INPUT_CONFIRM_DIALOG);
        // Make URL in the dialog message clickable
        if (mDialog != null) {
            TextView textView = (TextView) mDialog.findViewById(android.R.id.message);
            if (textView != null) {
                textView.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }

    private void updateVoiceModeSummary() {
        mVoicePreference.setSummary(
                getResources().getStringArray(R.array.voice_input_modes_summary)
                [mVoicePreference.findIndexOfValue(mVoicePreference.getValue())]);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case VOICE_INPUT_CONFIRM_DIALOG:
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (whichButton == DialogInterface.BUTTON_NEGATIVE) {
                            mVoicePreference.setValue(mVoiceModeOff);
                        } else if (whichButton == DialogInterface.BUTTON_POSITIVE) {
                            mOkClicked = true;
                        }
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivityInternal())
                        .setTitle(R.string.voice_warning_title)
                        .setPositiveButton(android.R.string.ok, listener)
                        .setNegativeButton(android.R.string.cancel, listener);

                // Get the current list of supported locales and check the current locale against
                // that list, to decide whether to put a warning that voice input will not work in
                // the current language as part of the pop-up confirmation dialog.
                boolean localeSupported = SubtypeSwitcher.isVoiceSupported(
                        this, Locale.getDefault().toString());

                final CharSequence message;
                if (localeSupported) {
                    message = TextUtils.concat(
                            getText(R.string.voice_warning_may_not_understand), "\n\n",
                                    getText(R.string.voice_hint_dialog_message));
                } else {
                    message = TextUtils.concat(
                            getText(R.string.voice_warning_locale_not_supported), "\n\n",
                                    getText(R.string.voice_warning_may_not_understand), "\n\n",
                                            getText(R.string.voice_hint_dialog_message));
                }
                builder.setMessage(message);
                AlertDialog dialog = builder.create();
                mDialog = dialog;
                dialog.setOnDismissListener(this);
                return dialog;
            default:
                Log.e(TAG, "unknown dialog " + id);
                return null;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (!mOkClicked) {
            // This assumes that onPreferenceClick gets called first, and this if the user
            // agreed after the warning, we set the mOkClicked value to true.
            mVoicePreference.setValue(mVoiceModeOff);
        }
    }

    private void refreshEnablingsOfKeypressSoundAndVibrationSettings(
            SharedPreferences sp, Resources res) {
        if (mKeypressVibrationDurationSettingsPref != null) {
            final boolean hasVibrator = VibratorCompatWrapper.getInstance(this).hasVibrator();
            final boolean vibrateOn = hasVibrator && sp.getBoolean(Settings.PREF_VIBRATE_ON,
                    res.getBoolean(R.bool.config_default_vibration_enabled));
            mKeypressVibrationDurationSettingsPref.setEnabled(vibrateOn);
        }

        if (mKeypressSoundVolumeSettingsPref != null) {
            final boolean soundOn = sp.getBoolean(Settings.PREF_SOUND_ON,
                    res.getBoolean(R.bool.config_default_sound_enabled));
            mKeypressSoundVolumeSettingsPref.setEnabled(soundOn);
        }
    }

    private void updateKeypressVibrationDurationSettingsSummary(
            SharedPreferences sp, Resources res) {
        if (mKeypressVibrationDurationSettingsPref != null) {
            mKeypressVibrationDurationSettingsPref.setSummary(
                    SettingsValues.getCurrentVibrationDuration(sp, res)
                            + res.getString(R.string.settings_ms));
        }
    }

    private void showKeypressVibrationDurationSettingsDialog() {
        final SharedPreferences sp = getPreferenceManager().getSharedPreferences();
        final Activity context = getActivityInternal();
        final Resources res = context.getResources();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.prefs_keypress_vibration_duration_settings);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                final int ms = Integer.valueOf(
                        mKeypressVibrationDurationSettingsTextView.getText().toString());
                sp.edit().putInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, ms).apply();
                updateKeypressVibrationDurationSettingsSummary(sp, res);
            }
        });
        builder.setNegativeButton(android.R.string.cancel,  new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        final View v = context.getLayoutInflater().inflate(
                R.layout.vibration_settings_dialog, null);
        final int currentMs = SettingsValues.getCurrentVibrationDuration(
                getPreferenceManager().getSharedPreferences(), getResources());
        mKeypressVibrationDurationSettingsTextView = (TextView)v.findViewById(R.id.vibration_value);
        final SeekBar sb = (SeekBar)v.findViewById(R.id.vibration_settings);
        sb.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
                final int tempMs = arg1;
                mKeypressVibrationDurationSettingsTextView.setText(String.valueOf(tempMs));
            }

            @Override
            public void onStartTrackingTouch(SeekBar arg0) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar arg0) {
                final int tempMs = arg0.getProgress();
                VibratorCompatWrapper.getInstance(context).vibrate(tempMs);
            }
        });
        sb.setProgress(currentMs);
        mKeypressVibrationDurationSettingsTextView.setText(String.valueOf(currentMs));
        builder.setView(v);
        builder.create().show();
    }

    private void updateKeypressSoundVolumeSummary(SharedPreferences sp, Resources res) {
        if (mKeypressSoundVolumeSettingsPref != null) {
            mKeypressSoundVolumeSettingsPref.setSummary(String.valueOf(
                    (int)(SettingsValues.getCurrentKeypressSoundVolume(sp, res) * 100)));
        }
    }

    private void showKeypressSoundVolumeSettingDialog() {
        final AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        final SharedPreferences sp = getPreferenceManager().getSharedPreferences();
        final Activity context = getActivityInternal();
        final Resources res = context.getResources();
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.prefs_keypress_sound_volume_settings);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                final float volume =
                        ((float)Integer.valueOf(
                                mKeypressSoundVolumeSettingsTextView.getText().toString())) / 100;
                sp.edit().putFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME, volume).apply();
                updateKeypressSoundVolumeSummary(sp, res);
            }
        });
        builder.setNegativeButton(android.R.string.cancel,  new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        final View v = context.getLayoutInflater().inflate(
                R.layout.sound_effect_volume_dialog, null);
        final int currentVolumeInt =
                (int)(SettingsValues.getCurrentKeypressSoundVolume(sp, res) * 100);
        mKeypressSoundVolumeSettingsTextView =
                (TextView)v.findViewById(R.id.sound_effect_volume_value);
        final SeekBar sb = (SeekBar)v.findViewById(R.id.sound_effect_volume_bar);
        sb.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
                final int tempVolume = arg1;
                mKeypressSoundVolumeSettingsTextView.setText(String.valueOf(tempVolume));
            }

            @Override
            public void onStartTrackingTouch(SeekBar arg0) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar arg0) {
                final float tempVolume = ((float)arg0.getProgress()) / 100;
                am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, tempVolume);
            }
        });
        sb.setProgress(currentVolumeInt);
        mKeypressSoundVolumeSettingsTextView.setText(String.valueOf(currentVolumeInt));
        builder.setView(v);
        builder.create().show();
    }
}
