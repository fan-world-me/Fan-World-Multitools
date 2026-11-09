package fan_world_me.multitoolshelp;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultitoolsHelp implements ModInitializer {
    public static final String MOD_ID = "multitoolshelp";
    public static final Logger LOGGER = LoggerFactory.getLogger("MultitoolsHelp");

    @Override
    public void onInitialize() {
        LOGGER.info("MultitoolsHelp loaded!");
        StrikeScheduler.init();
        GivefCommand.register();
        PlayerJoinHandler.register();
    }
}
