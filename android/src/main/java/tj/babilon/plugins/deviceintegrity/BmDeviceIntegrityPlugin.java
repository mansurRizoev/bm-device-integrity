package tj.babilon.plugins.deviceintegrity;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import com.google.android.play.core.integrity.IntegrityManager;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.IntegrityServiceException;
import com.google.android.play.core.integrity.IntegrityTokenRequest;
import com.google.android.play.core.integrity.IntegrityTokenResponse;
import com.google.android.play.core.integrity.StandardIntegrityManager;
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest;
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken;
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider;
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest;

/**
 * Тонкая обёртка над Google Play Integrity API. Токен НЕ расшифровывается на устройстве —
 * это делает только backend.
 */
public class BmDeviceIntegrityPlugin {

    public interface Callback<T> {
        void onSuccess(T value);

        void onError(String code, String message);
    }

    private final Context context;
    private StandardIntegrityTokenProvider standardProvider;

    public BmDeviceIntegrityPlugin(Context context) {
        this.context = context.getApplicationContext();
    }

    public void requestClassicToken(@NonNull String nonce, @Nullable Long cloudProjectNumber, Callback<String> cb) {
        IntegrityManager manager = IntegrityManagerFactory.create(context);
        IntegrityTokenRequest.Builder builder = IntegrityTokenRequest.builder().setNonce(nonce);
        if (cloudProjectNumber != null) {
            builder.setCloudProjectNumber(cloudProjectNumber);
        }
        Task<IntegrityTokenResponse> task = manager.requestIntegrityToken(builder.build());
        task.addOnSuccessListener((r) -> cb.onSuccess(r.token())).addOnFailureListener((e) -> cb.onError(errorCode(e), e.getMessage()));
    }

    public void prepareStandard(long cloudProjectNumber, Callback<Void> cb) {
        StandardIntegrityManager manager = IntegrityManagerFactory.createStandard(context);
        manager
            .prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(cloudProjectNumber).build())
            .addOnSuccessListener((provider) -> {
                standardProvider = provider;
                cb.onSuccess(null);
            })
            .addOnFailureListener((e) -> cb.onError(errorCode(e), e.getMessage()));
    }

    public void requestStandardToken(@NonNull String requestHash, Callback<String> cb) {
        StandardIntegrityTokenProvider provider = standardProvider;
        if (provider == null) {
            cb.onError("NOT_PREPARED", "Call prepareStandardIntegrity() first.");
            return;
        }
        provider
            .request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build())
            .addOnSuccessListener((StandardIntegrityToken t) -> cb.onSuccess(t.token()))
            .addOnFailureListener((e) -> cb.onError(errorCode(e), e.getMessage()));
    }

    private static String errorCode(Exception e) {
        if (e instanceof IntegrityServiceException) {
            return "INTEGRITY_ERROR_" + ((IntegrityServiceException) e).getErrorCode();
        }
        return "INTEGRITY_ERROR";
    }
}
