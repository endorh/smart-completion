package endorh.smartcompletion;

import endorh.smartcompletion.customization.SmartCompletionSettings;
import org.jetbrains.annotations.NonNls;

public class SmartCompletionMod {
   @NonNls public static final String MOD_ID = "smartcompletion";
   private static SmartCompletionSettings SETTINGS;
   public static SmartCompletionSettings getSmartCompletionSettings() {
      return SETTINGS;
   }

   public static void init() {
      init(false);
   }
   public static void init(boolean testing) {
      SETTINGS = new SmartCompletionSettings(testing);
   }
}
