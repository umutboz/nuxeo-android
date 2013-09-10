package org.nuxeo.android.testsfrag;

import org.nuxeo.android.documentprovider.LazyUpdatableDocumentsList;
import org.nuxeo.ecm.automation.client.jaxrs.model.Documents;


public class SimpleListFragment extends BaseSampleDocumentsListFragment {

	protected LazyUpdatableDocumentsList fetchDocumentsList(byte cacheParam)
			throws Exception {
		Documents docs = getNuxeoContext()
				.getDocumentManager()
				.query("select * from Document where ecm:mixinType != \"HiddenInNavigation\" AND ecm:currentLifeCycleState!='deleted' AND ecm:isCheckedInVersion = 0 order by dc:modified desc",
						null, null, null, 0, 10, cacheParam);
		if (docs != null) {
			return docs.asUpdatableDocumentsList();
		}
		throw new RuntimeException("fetch Operation did return null");
	}
	

}
