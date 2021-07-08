package org.thoughtcrime.securesms.preferences;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import org.session.libsession.utilities.TextSecurePreferences;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.components.SwitchPreferenceCompat;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.service.KeyCachingService;

import java.util.concurrent.TimeUnit;

import mobi.upod.timedurationpicker.TimeDurationPickerDialog;
import network.loki.messenger.R;

public class ThemesPreferenceFragment extends CorrectedPreferenceFragment implements InjectableType {

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    ApplicationContext.getInstance(activity).injectDependencies(this);
  }

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    this.findPreference(TextSecurePreferences.THEME_PURPLE).setOnPreferenceChangeListener(new ThemePurpleListener());
    this.findPreference(TextSecurePreferences.THEME_RED).setOnPreferenceClickListener(new ThemeRedListener());

    initializeVisibility();
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_app_protection);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (TextSecurePreferences.isPasswordDisabled(getContext())) {
      initializeScreenLockTimeoutSummary();
    }
  }

  private void initializeScreenLockTimeoutSummary() {
    long timeoutSeconds = TextSecurePreferences.getScreenLockTimeout(getContext());
    long hours          = TimeUnit.SECONDS.toHours(timeoutSeconds);
    long minutes        = TimeUnit.SECONDS.toMinutes(timeoutSeconds) - (TimeUnit.SECONDS.toHours(timeoutSeconds) * 60  );
    long seconds        = TimeUnit.SECONDS.toSeconds(timeoutSeconds) - (TimeUnit.SECONDS.toMinutes(timeoutSeconds) * 60);

    findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT)
        .setSummary(timeoutSeconds <= 0 ? getString(R.string.AppProtectionPreferenceFragment_none) :
                                          String.format("%02d:%02d:%02d", hours, minutes, seconds));
  }

  private void initializeVisibility() {
    if (TextSecurePreferences.isPasswordDisabled(getContext())) {
      KeyguardManager keyguardManager = (KeyguardManager)getContext().getSystemService(Context.KEYGUARD_SERVICE);
      if (!keyguardManager.isKeyguardSecure()) {
        ((SwitchPreferenceCompat)findPreference(TextSecurePreferences.SCREEN_LOCK)).setChecked(false);
        findPreference(TextSecurePreferences.SCREEN_LOCK).setEnabled(false);
      }
    } else {
      findPreference(TextSecurePreferences.SCREEN_LOCK).setVisible(false);
      findPreference(TextSecurePreferences.SCREEN_LOCK_TIMEOUT).setVisible(false);
    }
  }

  private class ThemePurpleListener implements Preference.OnPreferenceChangeListener {
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
      boolean enabled = (Boolean)newValue;

      TextSecurePreferences.setScreenLockEnabled(getContext(), enabled);

      Intent intent = new Intent(getContext(), KeyCachingService.class);
      intent.setAction(KeyCachingService.LOCK_TOGGLED_EVENT);
      getContext().startService(intent);
      return true;
    }
  }

  private class ThemeRedListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      new TimeDurationPickerDialog(getContext(), (view, duration) -> {
        if (duration == 0) {
          TextSecurePreferences.setScreenLockTimeout(getContext(), 0);
        } else {
          long timeoutSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
          TextSecurePreferences.setScreenLockTimeout(getContext(), timeoutSeconds);
        }

        initializeScreenLockTimeoutSummary();
      }, 0).show();

      return true;
    }
  }

}
