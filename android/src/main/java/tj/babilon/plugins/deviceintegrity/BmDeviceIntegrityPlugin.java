package tj.babilon.plugins.deviceintegrity;

import com.getcapacitor.Logger;

public class BmDeviceIntegrityPlugin {

    public String echo(String value) {
        Logger.info("Echo", value);
        return value;
    }
}
