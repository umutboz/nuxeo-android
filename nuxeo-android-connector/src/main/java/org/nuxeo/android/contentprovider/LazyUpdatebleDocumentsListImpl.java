package org.nuxeo.android.contentprovider;

import org.nuxeo.ecm.automation.client.jaxrs.AsyncCallback;
import org.nuxeo.ecm.automation.client.jaxrs.OperationRequest;
import org.nuxeo.ecm.automation.client.jaxrs.Session;
import org.nuxeo.ecm.automation.client.jaxrs.adapters.DocumentService;
import org.nuxeo.ecm.automation.client.jaxrs.model.Document;
import org.nuxeo.ecm.automation.client.jaxrs.model.Documents;
import org.nuxeo.ecm.automation.client.jaxrs.model.PathRef;

import android.util.Log;

public class LazyUpdatebleDocumentsListImpl extends LazyDocumentsListImpl
		implements LazyUpdatableDocumentsList {

	public LazyUpdatebleDocumentsListImpl (Session session, String nxql, String[] queryParams, String sortOrder, String schemas, int pageSize) {
		super(session, nxql, queryParams, sortOrder, schemas, pageSize);
	}

	public LazyUpdatebleDocumentsListImpl (OperationRequest fetchOperation, String pageParametrerName) {
		super(fetchOperation, pageParametrerName);
	}

	@Override
	public void updateDocument(Document updatedDocument) {
		updateDocument(updatedDocument, null);
	}

	@Override
	public void updateDocument(Document updatedDocument, OperationRequest updateOperation) {
		boolean updated = false;
		int updatedPage = 0;
		Document beforeUpdateDocument = null;
		int updatedIdx = 0;
		for (Integer pageIdx : pages.keySet()) {
			if (updated) {
				break;
			}
			Documents docs = pages.get(pageIdx);
			for (int i = 0; i <docs.size(); i++) {
				if (docs.get(i).getId().equals(updatedDocument.getId())) {
					updatedIdx = i;
					beforeUpdateDocument = docs.get(i);
					docs.set(i, updatedDocument);
					updatedPage = pageIdx;
					updated=true;
					break;
				}
			}
		}

		if (updated) {
			// send update to server
			final int page = updatedPage;
			final Document originalDocument = beforeUpdateDocument;
			final int docIdx = updatedIdx;
			if (updateOperation==null) {
				updateOperation = buildUpdateOperation(session, updatedDocument);
			}
			session.execDeferredUpdate(updateOperation, new AsyncCallback<Object>() {

				@Override
				public void onSuccess(String executionId, Object data) {
					notifyContentChanged(page);
					// start refreshing
					refreshAll();
				}

				@Override
				public void onError(String executionId, Throwable e) {
					// revert to previous
					pages.get(page).set(docIdx, originalDocument);
					notifyContentChanged(page);
				}
			});
			// notify UI
			notifyContentChanged(updatedPage);
		}
	}

	protected OperationRequest buildUpdateOperation(Session session, Document updatedDocument) {
		OperationRequest updateOperation = session.newRequest(DocumentService.UpdateDocument).setInput(updatedDocument);
		updateOperation.set("properties", updatedDocument.getDirtyPropertiesAsPropertiesString());
		updateOperation.set("save", true);
		return updateOperation;
	}

	protected OperationRequest buildCreateOperation(Session session, Document newDocument) {
		PathRef parent = new PathRef(newDocument.getParentPath());
		OperationRequest createOperation = session.newRequest(DocumentService.CreateDocument).setInput(parent);
		createOperation.set("type", newDocument.getType());
		createOperation.set("properties", newDocument.getDirtyPropertiesAsPropertiesString());
		if (newDocument.getName()!=null) {
			createOperation.set("name", newDocument.getName());
		}
		return createOperation;
	}


	@Override
	public void createDocument(Document newDocument) {
		createDocument(newDocument, null);
	}

	protected String addPendingCreatedDocument(Document newDoc) {
		pages.get(0).add(0, newDoc);
		notifyContentChanged(0);
		return newDoc.getId();
	}

	protected void removePendingCreatedDocument(String uuid) {
		Documents docs = pages.get(0);
		for (int idx = 0 ; idx < docs.size(); idx++) {
			if (docs.get(idx).getId().equals(uuid)) {
				docs.remove(idx);
				break;
			}
		}
		notifyContentChanged(0);
	}

	@Override
	public void createDocument(Document newDocument,
			OperationRequest createOperation) {

		final String key = addPendingCreatedDocument(newDocument);

		if (createOperation==null) {
			createOperation = buildCreateOperation(session, newDocument);
		}
		session.execDeferredUpdate(createOperation, new AsyncCallback<Object>() {

			@Override
			public void onSuccess(String executionId, Object data) {
				removePendingCreatedDocument(key);
				// start refreshing
				refreshAll();
			}

			@Override
			public void onError(String executionId, Throwable e) {
				// revert to previous
				removePendingCreatedDocument(key);
				Log.e(LazyUpdatebleDocumentsListImpl.class.getSimpleName(), "Deferred Creation failed", e);
			}
		});
	}

	@Override
	protected int computeTargetPage(int position) {
		if (position < pages.get(0).size()) {
			return 0;
		}
		else {
			return 1 + ( (position - pages.get(0).size()) / pageSize);
		}
	}

	@Override
	protected int getRelativePositionOnPage(int globalPosition, int pageIndex) {
		if (pageIndex==0) {
			return globalPosition;
		} else {
			return globalPosition  - pages.get(0).size() - (pageIndex-1) * pageSize;
		}
	}

}