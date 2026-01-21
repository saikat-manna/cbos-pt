package com.cbosgroup.cbos.core.documents;

import com.cbosgroup.cbos.core.actors.Signature;
import java.util.Map;

/**
 * Reperesents an actual real world document 
 */
public class Document {

	private String version;
	
	private DocumentFieldMetadata metadata;
	
	private Map<String, String> fieldValues;
	
	/**
	 * Where is the document actually stored , file , s3.Local file url , s3 url  
	 */
	private String physicalSorceUrl;
	
	/**
	 * Who has verified the document , can be system also
	 */
	private Signature signedBy ;
}
