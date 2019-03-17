package org.tensorflow.demo.download;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.support.annotation.IdRes;
import android.view.Window;
import android.widget.Toast;

import com.roughike.bottombar.BottomBar;
import com.roughike.bottombar.OnTabReselectListener;
import com.roughike.bottombar.OnTabSelectListener;

import org.tensorflow.demo.PersonFragment;
import org.tensorflow.demo.R;
import org.tensorflow.demo.download.DownloadFragment;

public class MainActivity extends Activity implements DownloadFragment.OnFragmentInteractionListener,
        PersonFragment.OnFragmentInteractionListener {

    private BottomBar bottomBar;
    private  FragmentManager fm;
    private Toast toast;
    private DownloadFragment downloadFrag;
    private PersonFragment personFrag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

       bottomBar = (BottomBar) findViewById(R.id.bottomBar);
        fm = getFragmentManager();

        findViews();
        initViews();

    }

    private void initViews() {
        bottomBar.setOnTabSelectListener(new OnTabSelectListener() {
            @Override
            public void onTabSelected(@IdRes int tabId) {
                final FragmentTransaction transaction = fm.beginTransaction();
                switch (tabId) {
                    case R.id.tab1:
                        if (downloadFrag == null){
                            downloadFrag = new DownloadFragment();
                            transaction.add(R.id.main_frag, downloadFrag);
                        }
                        else{
                            transaction.show(downloadFrag);
                        }
                        if (personFrag != null) {
                            transaction.hide(personFrag);
                        }
                        break;
                    case R.id.tab2:
                        if (personFrag == null){
                            personFrag = new PersonFragment();
                            transaction.add(R.id.main_frag, personFrag);
                        }
                        else{
                            transaction.show(personFrag);
                        }
                        if (downloadFrag != null) {
                            transaction.hide(downloadFrag);
                        }

                        break;
                }
                transaction.commit();
            }
        });

        bottomBar.setOnTabReselectListener(new OnTabReselectListener() {
            @Override
            public void onTabReSelected(@IdRes int tabId) {
                switch (tabId) {
                    case R.id.tab1:

                        break;
                    case R.id.tab2:

                        break;

                }
            }
        });
    }

    private void findViews() {
        bottomBar = (BottomBar) findViewById(R.id.bottomBar);
        //tv = (TextView) findViewById(R.id.tv);
    }

    @Override
    public void onFragmentInteraction(Uri uri) {

    }

}
