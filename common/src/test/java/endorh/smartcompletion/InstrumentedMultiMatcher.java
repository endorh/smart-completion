package endorh.smartcompletion;

public class InstrumentedMultiMatcher extends MultiMatcher {
   private int backTrackCount;
   private int partBackTrackCount;
   private boolean weakCheckAbort;

   @Override public synchronized MultiMatch match(String target, String query) {
      backTrackCount = 0;
      partBackTrackCount = 0;
      weakCheckAbort = false;
      return super.match(target, query);
   }

   @Override protected void backTrack() {
      backTrackCount++;
      super.backTrack();
   }

   @Override protected void partBackTrack() {
      partBackTrackCount++;
      super.partBackTrack();
   }

   @Override protected boolean weakMatchCheck() {
      boolean c = super.weakMatchCheck();
      weakCheckAbort = !c;
      return c;
   }

   public int getBackTrackCount() {
      return backTrackCount;
   }

   public int getPartBackTrackCount() {
      return partBackTrackCount;
   }

   public boolean didAbortWithWeakCheck() {
      return weakCheckAbort;
   }
}
