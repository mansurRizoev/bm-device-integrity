package tj.babilon.plugins.deviceintegrity;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@CapacitorPlugin(name = "BmDeviceIntegrityPlugin")
public class BmDeviceIntegrityPluginPlugin extends Plugin {

    /** base64 URL-safe, без padding и переносов строк (требование Play Integrity classic). */
    private static final Pattern CLASSIC_NONCE = Pattern.compile("^[A-Za-z0-9_-]+$");

    private BmDeviceIntegrityPlugin implementation;

    @Override
    public void load() {
        implementation = new BmDeviceIntegrityPlugin(getContext());
    }

    @PluginMethod
    public void isSupported(PluginCall call) {
        int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getContext());
        boolean ok = status == ConnectionResult.SUCCESS;
        JSObject ret = new JSObject();
        ret.put("supported", ok);
        ret.put("platform", "android");
        ret.put("googlePlayServices", ok);
        if (!ok) {
            ret.put("reason", "Google Play Services unavailable (status " + status + ")");
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void requestIntegrityToken(PluginCall call) {
        String nonce = call.getString("nonce");
        if (nonce == null || !CLASSIC_NONCE.matcher(nonce).matches()) {
            call.reject("nonce must be base64 URL-safe without padding or line breaks", "INVALID_ARGUMENT");
            return;
        }
        int nonceBytes = (nonce.length() * 3) / 4; // размер после декодирования base64 без padding
        if (nonceBytes < 16 || nonceBytes > 500) {
            call.reject("nonce must decode to 16-500 bytes", "INVALID_ARGUMENT");
            return;
        }
        Long project = null;
        if (call.hasOption("cloudProjectNumber")) {
            project = readCloudProjectNumber(call);
            if (project == null) {
                return;
            }
        }
        implementation.requestClassicToken(nonce, project, tokenCallback(call));
    }

    @PluginMethod
    public void prepareStandardIntegrity(PluginCall call) {
        Long project = readCloudProjectNumber(call);
        if (project == null) {
            return;
        }
        implementation.prepareStandard(
            project,
            new BmDeviceIntegrityPlugin.Callback<Void>() {
                @Override
                public void onSuccess(Void value) {
                    call.resolve();
                }

                @Override
                public void onError(String code, String message) {
                    call.reject(message, code);
                }
            }
        );
    }

    @PluginMethod
    public void requestStandardIntegrityToken(PluginCall call) {
        String requestHash = call.getString("requestHash");
        if (requestHash == null || requestHash.isEmpty()) {
            call.reject("requestHash is required", "INVALID_ARGUMENT");
            return;
        }
        if (requestHash.getBytes(StandardCharsets.UTF_8).length > 500) {
            call.reject("requestHash must be at most 500 bytes", "INVALID_ARGUMENT");
            return;
        }
        implementation.requestStandardToken(requestHash, tokenCallback(call));
    }

    /** Положительное целое число; иначе отклоняет вызов и возвращает {@code null}. */
    private static Long readCloudProjectNumber(PluginCall call) {
        Object raw = call.getData().opt("cloudProjectNumber");
        if (raw instanceof Number) {
            Number n = (Number) raw;
            long value = n.longValue();
            if (value > 0 && n.doubleValue() == (double) value) {
                return value;
            }
        }
        call.reject("cloudProjectNumber must be a positive integer", "INVALID_ARGUMENT");
        return null;
    }

    @PluginMethod
    public void generateKey(PluginCall call) {
        call.unavailable("App Attest is only available on iOS.");
    }

    @PluginMethod
    public void attestKey(PluginCall call) {
        call.unavailable("App Attest is only available on iOS.");
    }

    @PluginMethod
    public void generateAssertion(PluginCall call) {
        call.unavailable("App Attest is only available on iOS.");
    }

    private BmDeviceIntegrityPlugin.Callback<String> tokenCallback(PluginCall call) {
        return new BmDeviceIntegrityPlugin.Callback<String>() {
            @Override
            public void onSuccess(String token) {
                JSObject ret = new JSObject();
                ret.put("token", token);
                call.resolve(ret);
            }

            @Override
            public void onError(String code, String message) {
                call.reject(message, code);
            }
        };
    }
}
