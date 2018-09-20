package text.xiaolong.com.socketapp;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * @Description:
 * @author: Xiaolong
 * @Date: 2018/9/20
 */
public class AlpAdapter extends RecyclerView.Adapter<AlpAdapter.MyViewHolder> {
    private List<String> lists;

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        public TextView mTextView;

        public MyViewHolder(TextView v) {
            super(v);
            mTextView = v;
        }
    }

    public AlpAdapter(List<String> lists) {
        this.lists = lists;
    }

    public void addMsg(String msg) {
        lists.add(msg);
        notifyDataSetChanged();
    }

    @Override
    public AlpAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent,
                                                      int viewType) {
        TextView v = (TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.my_text_view, parent, false);
        MyViewHolder vh = new MyViewHolder(v);
        return vh;
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        holder.mTextView.setText(lists.get(position));

    }

    @Override
    public int getItemCount() {
        return lists.size();
    }
}