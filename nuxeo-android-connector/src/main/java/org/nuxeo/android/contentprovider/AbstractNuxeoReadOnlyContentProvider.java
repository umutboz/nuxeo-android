package org.nuxeo.android.contentprovider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.nuxeo.android.context.NuxeoContext;
import org.nuxeo.android.cursor.NuxeoAssetCursor;
import org.nuxeo.android.cursor.NuxeoDocumentCursor;
import org.nuxeo.android.cursor.UUIDMapper;
import org.nuxeo.android.documentprovider.DocumentProvider;
import org.nuxeo.android.documentprovider.LazyDocumentsList;
import org.nuxeo.ecm.automation.client.android.AndroidAutomationClient;
import org.nuxeo.ecm.automation.client.android.AndroidResponseCacheManager;
import org.nuxeo.ecm.automation.client.cache.StreamHelper;
import org.nuxeo.ecm.automation.client.jaxrs.Session;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;


/**
 *
 * For {@link NuxeoDocumentCursor}
 *
 * content:/nuxeo/documents                  : access to all documents
 * content:/nuxeo/documents/<UUID>           : access to document with given UUID
 *
 * content:/nuxeo/<providername>             : access to documents in the given provider
 * content:/nuxeo/<providername>/UUID        : access to document with UUID in the given provider
 *
 * For {@link NuxeoAssetCursor}
 *
 * content:/nuxeo/icons/<subPath>            : access to small Nuxeo icon of the given path
 *
 * content:/nuxeo/blobs/<UUID>               : access to main blog of the doc with the given UUID
 * content:/nuxeo/blobs/<UUID>/<idx>         : access to blog [idx] of the doc with the given UUID
 * content:/nuxeo/blobs/<UUID>/<subPath>     : access to blog in the field <subpath> of the doc with the given UUID

 * @author tiry
 *
 */

public abstract class AbstractNuxeoReadOnlyContentProvider extends ContentProvider {

	public static final String ALL_DOCUMENTS = "documents";
	public static final String ICONS = "icons";

	protected UUIDMapper mapper;

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean onCreate() {
		// don't init the NuxeoSession now !!!
		mapper = new UUIDMapper();
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] columns, String selection,
			String[] selectionArgs, String sortOrder) {

		List<String> segments = uri.getPathSegments();
		if (segments.size()<3) {
			return null;
		}
		String providerName = segments.get(2);
		if (ALL_DOCUMENTS.equals(providerName)) {
			String nxql = buildNXQLQuery(selection, selectionArgs, sortOrder);
			return buildCursor(nxql, selection, selectionArgs, sortOrder);
		} else if (ICONS.equals(providerName)){
			return new NuxeoAssetCursor(getSession(), getContext());
		} else {
			DocumentProvider providerService = ((AndroidAutomationClient)getSession().getClient()).getDocumentProvider();
			LazyDocumentsList docList = providerService.getReadOnlyProvider(providerName, getSession());
			if (docList!=null) {
				return new NuxeoDocumentCursor(docList);
			}
		}
		return null;
	}

	protected Session getSession() {
		return NuxeoContext.get(getContext()).getSession();
	}

	protected NuxeoDocumentCursor buildCursor(String nxql, String selection,String[] selectionArgs, String sortOrder) {
		return new NuxeoDocumentCursor(getSession(),nxql, selectionArgs, sortOrder,getSchemas(), getPageSize(), mapper, false);
	}

	protected abstract int getPageSize();

	protected String getSchemas() {
		return "common,dublincore";
	}

	protected String buildNXQLQuery(String selection,
			String[] selectionArgs, String sortOrder) {

		String nxql = "select * from Document ";
		if (selection!=null) {
			nxql = nxql + " where " + selection;
		}

		if (sortOrder!=null && !sortOrder.equals("")) {
			nxql = nxql + " order by " + sortOrder;
		}

		return nxql;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public String getType(Uri arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AssetFileDescriptor openAssetFile(Uri uri, String mode)
			throws FileNotFoundException {
		return super.openAssetFile(uri, mode);
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {

		HttpUriRequest request = new HttpGet("http://10.0.2.2:8080/nuxeo/icons/file.gif");
		try {
			HttpResponse response = ((HttpAutomationClient) getSession().getClient()).http().execute(request);
			File cacheDir = getContext().getExternalCacheDir();
			if (cacheDir==null) {
				Log.w(AndroidResponseCacheManager.class.getSimpleName(), "No external directory accessible, using main storage");
				cacheDir = getContext().getFilesDir();
			}
			String fileName = System.currentTimeMillis() + "fileTest.gif";
			File streamFile = new File(cacheDir, fileName);
			InputStream is = response.getEntity().getContent();
			try {
				FileOutputStream out = new FileOutputStream(streamFile);
				StreamHelper.copy(is, out);
				is.close();
				out.close();
				return ParcelFileDescriptor.open(streamFile, ParcelFileDescriptor.MODE_READ_ONLY);

			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return super.openFile(uri, mode);
	}


}
