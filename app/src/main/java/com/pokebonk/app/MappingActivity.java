package com.pokebonk.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.List;

/**
 * 映射按键设置：三列表格（游戏内功能 / 手柄按键 / 绑定目标键）。
 * 点按行 → 修改手柄按键；长按行 → 修改绑定的目标键。
 */
public class MappingActivity extends Activity {

    private List<GamepadMap.Entry> entries;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapping);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());

        entries = GamepadMap.load(this);
        adapter = new Adapter();
        ((ListView) findViewById(R.id.list_mapping)).setAdapter(adapter);

        findViewById(R.id.btn_reset).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                GamepadMap.reset(MappingActivity.this);
                entries = GamepadMap.load(MappingActivity.this);
                adapter.notifyDataSetChanged();
                Toast.makeText(MappingActivity.this, "已恢复默认映射", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    /** 修改某行的手柄按键 */
    private void pickButton(final int index) {
        final List<Integer> buttons = GamepadMap.allButtons();
        String[] names = new String[buttons.size()];
        for (int i = 0; i < buttons.size(); i++) names[i] = GamepadMap.buttonName(buttons.get(i));
        new AlertDialog.Builder(this)
                .setTitle("选择手柄按键（功能：" + entries.get(index).func + "）")
                .setItems(names, (d, which) -> {
                    entries.get(index).button = buttons.get(which);
                    GamepadMap.save(this, entries);
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 修改某行绑定的目标键 */
    private void pickTarget(final int index) {
        final List<String> targets = GamepadMap.allTargetNames();
        new AlertDialog.Builder(this)
                .setTitle("绑定目标键（功能：" + entries.get(index).func + "）")
                .setItems(targets.toArray(new String[0]), (d, which) -> {
                    entries.get(index).target = GamepadMap.targetFromName(targets.get(which));
                    GamepadMap.save(this, entries);
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(MappingActivity.this)
                        .inflate(R.layout.item_mapping, parent, false);
            }
            GamepadMap.Entry e = entries.get(position);
            ((TextView) v.findViewById(R.id.col_func)).setText(e.func);
            ((TextView) v.findViewById(R.id.col_button)).setText(GamepadMap.buttonName(e.button));
            ((TextView) v.findViewById(R.id.col_target)).setText(GamepadMap.targetName(e.target));
            v.setOnClickListener(view -> pickButton(position));
            v.setOnLongClickListener(view -> { pickTarget(position); return true; });
            return v;
        }
    }
}
