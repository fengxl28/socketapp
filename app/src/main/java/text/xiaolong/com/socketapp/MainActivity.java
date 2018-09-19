package text.xiaolong.com.socketapp;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.tv_client)
    TextView tvClient;
    @BindView(R.id.tv_server)
    TextView tvServer;

    @OnClick(R.id.tv_client) void tv_client() {
        tvClient.setClickable(false);
        tvServer.setVisibility(View.GONE);
        initAlpFragment(1);
    }

    @OnClick(R.id.tv_server) void tv_server() {
        tvServer.setClickable(false);
        tvClient.setVisibility(View.GONE);
        initAlpFragment(2);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }


    private void initAlpFragment(int type) {
        AlpFragment alpFragment = (AlpFragment) getSupportFragmentManager().findFragmentById(R.id.framelayout);
        if (alpFragment == null) {
            alpFragment = AlpFragment.newInstance(type);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.add(R.id.framelayout, alpFragment);
            transaction.commit();
        }
    }


}
