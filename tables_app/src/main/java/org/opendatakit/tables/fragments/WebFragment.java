/*
 * Copyright (C) 2014 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.tables.fragments;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;

import org.opendatakit.common.android.application.CommonApplication;
import org.opendatakit.common.android.listener.DatabaseConnectionListener;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.common.android.views.Data;
import org.opendatakit.common.android.views.ExecutorContext;
import org.opendatakit.common.android.views.ExecutorProcessor;
import org.opendatakit.common.android.views.ICallbackFragment;
import org.opendatakit.database.service.OdkDbInterface;
import org.opendatakit.tables.R;
import org.opendatakit.tables.activities.AbsBaseActivity;
import org.opendatakit.tables.application.Tables;
import org.opendatakit.tables.utils.Constants;
import org.opendatakit.tables.utils.IntentUtil;
import org.opendatakit.tables.utils.WebViewUtil;
import org.opendatakit.tables.views.webkits.*;

import android.os.Bundle;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

/**
 * Displays an HTML file that is not associated with a particular table.
 * Consequently it does not add a data JavaScript interface to its 
 * {@link WebView}. To display data about a table, see 
 * {@link AbsWebTableFragment} and its subclasses.
 * @author sudar.sam@gmail.com
 *
 */
public class WebFragment extends AbsBaseFragment implements IWebFragment, ICallbackFragment {
  
  private static final String TAG = WebFragment.class.getSimpleName();
  private static final String RESPONSE_JSON = "responseJSON";

  private static final int ID = R.layout.web_view_container;
  
  /** The name of the file this fragment is displaying. */
  protected String mFileName;
  
  /** The {@link Control} object that was jused to generate the
   * {@link ControlIf} that was passed to the {@link WebView}. This reference
   * must be saved to prevent garbage collection of the {@link WeakReference}
   * in {@link ControlIf}.
   */
  protected Control mControlReference;

  protected Data mDataReference;
  LinkedList<String> queueResponseJSON = new LinkedList<String>();

  private DatabaseConnectionListener listener = null;

  @Override
  public String retrieveFileNameFromBundle(Bundle bundle) {
    if ( bundle == null ) {
      return null;
    }
    String fileName = IntentUtil.retrieveFileNameFromBundle(bundle);
    return fileName;
  }

  @Override
  public void putFileNameInBundle(Bundle bundle) {
    if (this.getFileName() != null) {
      bundle.putString(Constants.IntentKeys.FILE_NAME, this.getFileName());
    }
  }
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // AppName may not be available...
    // Get the file name. Saved state gets precedence. Then arguments.
    String retrievedFileName = retrieveFileNameFromBundle(savedInstanceState);
    if (retrievedFileName == null) {
      retrievedFileName = this.retrieveFileNameFromBundle(this.getArguments());
    }
    this.mFileName = retrievedFileName;
    if ( savedInstanceState != null && savedInstanceState.containsKey(RESPONSE_JSON)) {
      String[] pendingResponseJSON = savedInstanceState.getStringArray(RESPONSE_JSON);
      queueResponseJSON.addAll(Arrays.asList(pendingResponseJSON));
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    putFileNameInBundle(outState);
    if ( !queueResponseJSON.isEmpty() ) {
      String[] qra = queueResponseJSON.toArray(new String[queueResponseJSON.size()]);
      outState.putStringArray(RESPONSE_JSON, qra);
    }
  }
  
  @Override
  public View onCreateView(
      LayoutInflater inflater,
      ViewGroup container,
      Bundle savedInstanceState) {
    WebLogger.getLogger(getAppName()).d(TAG, "[onCreateView] activity is: " + this.getActivity());
    
    View v = inflater.inflate(
        R.layout.web_view_container,
        container,
        false);

    WebView webView = (WebView) v.findViewById(R.id.webkit);
    
    WebView result = WebViewUtil.getODKCompliantWebView((AbsBaseActivity) getActivity(), webView);
    return v;
  }

  @Override
  public String getFileName() {
    return this.mFileName;
  }
  
  @Override
  public void setFileName(String relativeFileName) {
    this.mFileName = relativeFileName;
    databaseAvailable();
  }

  /**
   * @see IWebFragment#createControlObject()
   */
  @Override
  public Control createControlObject() {
    try {
      Control result = new Control((AbsBaseActivity) this.getActivity(), null, null);
      return result;
    } catch (RemoteException e) {
      WebLogger.getLogger(getAppName()).e(TAG, "Unable to access database");
      return null;
    }
  }

  @Override
  public Data getDataReference() {
    if ( mDataReference == null ) {
      mDataReference = new Data(this);
    }
    return mDataReference;
  }


  @Override
  public void setWebKitVisibility() {
    if ( getView() == null ) {
      return;
    }
    
    WebView webView = (WebView) getView().findViewById(R.id.webkit);
    TextView noDatabase = (TextView) getView().findViewById(android.R.id.empty);
    
    if ( Tables.getInstance().getDatabase() != null ) {
      webView.setVisibility(View.VISIBLE);
      noDatabase.setVisibility(View.GONE);
    } else {
      webView.setVisibility(View.GONE);
      noDatabase.setVisibility(View.VISIBLE);
    }
  }
  
  @Override
  public void databaseAvailable() {

    if ( Tables.getInstance().getDatabase() != null && getView() != null && getFileName() != null ) {

      Control control = this.createControlObject();
      if ( control == null ) {
        return;
      }

      WebView webView = (WebView) getView().findViewById(org.opendatakit.tables.R.id.webkit);
      webView.addJavascriptInterface(
          control.getJavascriptInterfaceWithWeakReference(),
          Constants.JavaScriptHandles.CONTROL);
      setWebKitVisibility();
      Data data = this.getDataReference();
      webView.addJavascriptInterface(
              data.getJavascriptInterfaceWithWeakReference(),
              Constants.JavaScriptHandles.DATAIF);
      setWebKitVisibility();
      // save the strong reference
      this.mControlReference = control;
      WebViewUtil.displayFileInWebView(
          getActivity(),
          ((AbsBaseActivity) getActivity()).getAppName(),
          webView,
          this.getFileName());
    }

    if ( listener != null ) {
      listener.databaseAvailable();
    }
  }

  @Override
  public void databaseUnavailable() {
    setWebKitVisibility();
    if ( listener != null ) {
      listener.databaseUnavailable();
    }
  }

  @Override
  public void signalResponseAvailable(String responseJSON) {
    this.queueResponseJSON.push(responseJSON);
    final WebView webView = (WebView) getView().findViewById(org.opendatakit.tables.R.id.webkit);
    this.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        webView.loadUrl("javascript:datarsp.responseAvailable();");
      }
    });
  }

  @Override
  public String getResponseJSON() {
    if ( queueResponseJSON.isEmpty() ) {
      return null;
    }
    String responseJSON = queueResponseJSON.removeFirst();
    return responseJSON;
  }

  @Override
  public ExecutorProcessor newExecutorProcessor(ExecutorContext context) {
    return new TableDataExecutorProcessor(context);
  }
  @Override
  public void registerDatabaseConnectionBackgroundListener(DatabaseConnectionListener listener) {
    this.listener = listener;
  }

  @Override
  public OdkDbInterface getDatabase() {
    return ((CommonApplication) this.getActivity().getApplication()).getDatabase();
  }
}
