package github.taivo.parsepushplugin;

import android.app.Application;

import com.parse.Parse;
import com.parse.ParseObject;
import com.parse.Parse.Configuration.Builder;
import com.parse.ParseInstallation;
import com.parse.GetCallback;
import com.parse.SaveCallback;
import com.parse.DeleteCallback;
import com.parse.ParseException;

import github.taivo.parsepushplugin.ParsePushConfigReader;
import github.taivo.parsepushplugin.ParsePushConfigException;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessaging;
import android.util.Log;
import android.widget.Toast;

/*
   Why is this Application subclass needed?
      - Cordova does not define an Application class, only Activity.
      - The android cold start sequence is: create Application -> ... --> handle push --> ... -> launch Activity,
      - Without configuring an Application class, the app would crash during push notification cold start because
         Parse.Push is not initialized before the "handle push" phase.

   How does Android know to use this subclass as the main application class?
      - In AndroidManifest.xml, the <application> class has an attribute "android:name" that points to your designated main application class.
      - This plugin automatically sets android:name during plugin installation IFF it doesn't exist.
      - If you write your own MainApplication class in your app package, be sure to manually set android:name="MainApplication"
      - If your MainApplication resides in a package other than your main app package, the full path must be specified,
         i.e., android:name="com.custom.package.MainApplication"
*/
public class ParsePushApplication extends Application {
  public static final String LOGTAG = "ParsePushApplication";

  @Override
  public void onCreate() {
    super.onCreate();

    try {
      // Other ways to call ParsePushReaderConfig:
      //
      // - Tell the reader to parse custom parameters, e.g., <preference name="CustomParam1" value="foo" />
      //   ParsePushConfigReader config = new ParsePushConfigReader(getApplicationContext(), null, new String[] {"CustomParam1", "CustomParam2"});
      //
      // - If you write your own MainApplication in your app package, just import com.yourpackage.R and skip detecting R.xml.config
      //   ParsePushConfigReader config = new ParsePushConfigReader(getApplicationContext(), R.xml.config, null);
      //

      // Simple config reading for opensource parse-server:
      // 1st null to detect R.xml.config resource id, 2nd null indicates no custom config param
      //ParsePushConfigReader config = new ParsePushConfigReader(getApplicationContext(), null, null);
      //
      //Parse.initialize(new Parse.Configuration.Builder(this)
      //   .applicationId(config.getAppId())
      //   .server(config.getServerUrl()) // The trailing slash is important, e.g., https://mydomain.com:1337/parse/
      //   .build()
      //);

      //
      // Support parse.com and opensource parse-server
      // 1st null to detect R.xml.config
      ParsePushConfigReader config = new ParsePushConfigReader(getApplicationContext(), null,
          new String[] { "ParseClientKey" });
      Log.d(LOGTAG, "ServerUrl " + config.getServerUrl());
      Log.d(LOGTAG, "NOTE: The trailing slash is important, e.g., https://mydomain.com:1337/parse/");
      Log.d(LOGTAG, "NOTE: Set the clientKey if your server requires it, otherwise it can be null");
      //
      // initialize for use with opensource parse-server
      Parse.initialize(new Parse.Configuration.Builder(this).applicationId(config.getAppId())
           .server(config.getServerUrl()).clientKey(config.getClientKey()).build());

      Log.d(LOGTAG, "Saving Installation in background");
      FirebaseInstanceId.getInstance().getInstanceId()
        .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
            @Override
            public void onComplete(Task<InstanceIdResult> task) {
                if (!task.isSuccessful()) {
                    Log.w(LOGTAG, "getInstanceId failed", task.getException());
                    return;
                }

                // Get new Instance ID token
                String token = task.getResult().getToken();

                ParseInstallation inst = ParseInstallation.getCurrentInstallation();
                inst.setDeviceToken(token);
                inst.saveInBackground(new SaveCallback() {
                    @Override
                    public void done(ParseException ex) {
                      if (null != ex) {
                        Log.e(LOGTAG, ex.toString());
                      } else {
                        Log.e(LOGTAG, "Installation saved");
                      }
                    }
                });
            }
        });
      //
      // save installation. Parse.Push will need this to push to the correct device
/*      fetchCurrentInstallation(new FetchCompleteCallback() {
        @Override
        public void onComplete() {
          ParseInstallation inst = ParseInstallation.getCurrentInstallation();
          if (inst != null && (inst.getDeviceToken() == null || inst.getDeviceToken().isEmpty()))
            inst.deleteInBackground(new DeleteCallback() {
              @Override
              public void done(ParseException ex) {
                if (null != ex) {
                    Log.e(LOGTAG, ex.toString());
                } else {
                    Log.e(LOGTAG, "Installation deleted");
                }
                saveCurrentInstallation();
              }
            });
          else
            saveCurrentInstallation();
        }
      });
      */
    } catch (Exception e) {
      Log.e(LOGTAG, e.toString());
    }

  }

  private interface FetchCompleteCallback {
    public void onComplete();
  }

  private void fetchCurrentInstallation(FetchCompleteCallback onCompletion) {
    try {
      ParseInstallation.getCurrentInstallation().fetchInBackground(new GetCallback<ParseObject>() {
        @Override
        public void done(ParseObject obj, ParseException e) {
          onCompletion.onComplete();
        }
      });
    } catch (Exception e) {
      Log.e(LOGTAG, e.toString());
      onCompletion.onComplete();
    }
  }

  private void saveCurrentInstallation() {
    fetchCurrentInstallation(new FetchCompleteCallback() {
      @Override
      public void onComplete() {

          FirebaseInstanceId.getInstance().getInstanceId()
        .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
            @Override
            public void onComplete(Task<InstanceIdResult> task) {
                if (!task.isSuccessful()) {
                    Log.w(LOGTAG, "getInstanceId failed", task.getException());
                    return;
                }

                // Get new Instance ID token
                String token = task.getResult().getToken();

                // Log and toast
                ParseInstallation inst = ParseInstallation.getCurrentInstallation();
                inst.setDeviceToken(token);
                inst.saveInBackground(new SaveCallback() {
                    @Override
                    public void done(ParseException ex) {
                      if (null != ex) {
                        Log.e(LOGTAG, ex.toString());
                      } else {
                        Log.e(LOGTAG, "Installation saved");
                      }
                    }
                });
            }
        });

      }
    });
  }
}
