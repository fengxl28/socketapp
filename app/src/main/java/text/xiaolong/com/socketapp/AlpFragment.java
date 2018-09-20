package text.xiaolong.com.socketapp;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.mwee.android.alp.IMsgReceiver;
import com.mwee.android.alp.PushClient;
import com.mwee.android.alp.PushServer;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * @Description:
 * @author: Xiaolong
 * @Date: 2018/9/19
 */
public class AlpFragment extends Fragment {

    @BindView(R.id.et_content)
    EditText etContent;
    @BindView(R.id.send)
    Button send;
    Unbinder unbinder;
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;

    private int type = 0; //1:client  2:server
    private AlpAdapter alpAdapter;

    public static AlpFragment newInstance(int type) {
        AlpFragment fragment = new AlpFragment();
        fragment.type = type;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_alp, container, false);
        unbinder = ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String content = etContent.getText().toString();
                sendMsg(content);
            }
        });

        alpAdapter = new AlpAdapter(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(alpAdapter);
        initAlp();
    }


    private void initAlp() {
        if (type == 1) {
            initClient();
        } else if (type == 2) {
            initServer();
        }
    }


    private void sendMsg(String s) {
        if (TextUtils.isEmpty(s)) {
            return;
        }
        alpAdapter.addMsg("发送： " + s);
        if (type == 1) {
            PushClient.getInstance().pushMsg(s);
        } else if (type == 2) {
            PushServer.getInstance().pushMsg(s);
        }
    }


    private void initClient() {
        PushClient.getInstance().init(getContext());
        PushClient.getInstance().startClient("10.188.191.246", 45764, new IMsgReceiver() {
            @Override
            public void receive(String uniq, String param) {
                receiveMsg(param);
            }

            @Override
            public void connected() {

            }

            @Override
            public void disconnected() {

            }
        });
    }

    private void initServer() {
        PushServer.getInstance().startServer(45764, new IMsgReceiver() {
            @Override
            public void receive(String uniq, String param) {
                receiveMsg(param);
            }

            @Override
            public void connected() {

            }

            @Override
            public void disconnected() {

            }
        });
    }

    private void receiveMsg(String msg){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                alpAdapter.addMsg("接收： " + msg);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
        if (type == 1) {
            PushClient.getInstance().disConnect();
        } else if (type == 2) {
            PushServer.getInstance().finishServer();
        }
    }
}
