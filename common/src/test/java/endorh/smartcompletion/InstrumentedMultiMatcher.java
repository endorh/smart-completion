package endorh.smartcompletion;

public class InstrumentedMultiMatcher extends MultiMatcher{
	private int backTrackCount;
	private int partBackTrackCount;
	private boolean dumbCheckAbort;
	
	@Override public synchronized MultiMatch match(String target, String query) {
		backTrackCount = 0;
		partBackTrackCount = 0;
		dumbCheckAbort = false;
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
	
	@Override protected boolean dumbMatchCheck() {
		boolean c = super.dumbMatchCheck();
		dumbCheckAbort = !c;
		return c;
	}
	
	public int getBackTrackCount() {
		return backTrackCount;
	}
	
	public int getPartBackTrackCount() {
		return partBackTrackCount;
	}
	
	public boolean didAbortWithDumbCheck() {
		return dumbCheckAbort;
	}
}
