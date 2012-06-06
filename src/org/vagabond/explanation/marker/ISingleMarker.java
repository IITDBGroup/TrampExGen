package org.vagabond.explanation.marker;

public interface ISingleMarker {

	public String getTid ();
	public int getTidId();
	public String getRel ();
	public int getRelId();
	public boolean isSubsumed (ISingleMarker other);
	public int getSize ();
	public String toUserString();
	public String toUserStringNoRel();
	
}
