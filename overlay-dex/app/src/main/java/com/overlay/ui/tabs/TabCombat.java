package com.overlay.ui.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.overlay.KeyAuthManager;
import com.overlay.RadarView;
import com.overlay.auth.FeatureManager;
import com.overlay.ui.UIFactory;
import com.overlay.service.NativeSender;

import java.util.List;

public class TabCombat {
    public static LinearLayout build(Context ctx, SharedPreferences prefs, KeyAuthManager authManager, RadarView radar, int realScreenW, int realScreenH, ModernDialogShower dialogShower) {
        LinearLayout t = new LinearLayout(ctx); 
        t.setOrientation(LinearLayout.VERTICAL);

        t.addView(UIFactory.card(ctx, l -> {
            LinearLayout cols = new LinearLayout(ctx); 
            cols.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout ac = new LinearLayout(ctx); 
            ac.setOrientation(LinearLayout.VERTICAL);
            ac.addView(UIFactory.secTitle(ctx, "AIMBOT"));
            ac.addView(UIFactory.checkRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Aimbot All", "aimbot_enable", false));
            ac.addView(UIFactory.vgap(ctx, 8));
            ac.addView(UIFactory.secTitle(ctx, "LING MODE"));
            ac.addView(UIFactory.radioRowVertical(ctx, prefs, authManager, radar, realScreenW, realScreenH, "ling_mode", new String[]{"Off", "Manual", "Auto"}));

            cols.addView(ac, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            View vd = new View(ctx);
            vd.setLayoutParams(new LinearLayout.LayoutParams(UIFactory.dp(ctx, 1), LinearLayout.LayoutParams.MATCH_PARENT));
            vd.setBackgroundColor(UIFactory.C_DIVIDER); 
            cols.addView(vd);

            LinearLayout rc = new LinearLayout(ctx); 
            rc.setOrientation(LinearLayout.VERTICAL);
            rc.setPadding(UIFactory.dp(ctx, 10), 0, 0, 0);
            rc.addView(UIFactory.secTitle(ctx, "RETRIBUTION"));
            rc.addView(UIFactory.checkRowSpanned(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Buff (", "Blue", " & ", "Red", ")", "retri_buff", false));
            rc.addView(UIFactory.checkRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Lord", "retri_lord", false));
            rc.addView(UIFactory.checkRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Turtle", "retri_turtle", false));
            rc.addView(UIFactory.checkRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Litho", "retri_litho", false));
            cols.addView(rc, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            l.addView(cols);
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "LOCK HERO"));
            l.addView(UIFactory.toggleRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Enable Hero Lock", "Prioritize specific target", "lock_hero_enable", false));
            
            String currentHero = prefs.getString("locked_hero_name", "");
            if (currentHero.isEmpty()) currentHero = "None";

            final TextView[] btnHeroRef = new TextView[1];
            btnHeroRef[0] = (TextView) UIFactory.btn(ctx, "Select Hero: [" + currentHero + "]", UIFactory.C_BTN_DRK, () -> {
                List<String> listHero = radar.getActiveEnemyNames();
                if (listHero.isEmpty()) {
                    Toast.makeText(ctx, "Enemy not detected yet!", Toast.LENGTH_SHORT).show();
                } else {
                    String[] items = listHero.toArray(new String[0]);
                    dialogShower.showModernDialog("TARGET LOCK HERO", items, selected -> {
                        prefs.edit().putString("locked_hero_name", selected).apply();
                        if (btnHeroRef[0] != null) btnHeroRef[0].setText("Select Hero: [" + selected + "]");
                        NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
                    });
                }
            });
            l.addView(btnHeroRef[0]);
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "HERO SETTING"));
            
            String currentCombo = prefs.getString("selected_combo", "none");
            String displayCombo = "None";
            if (currentCombo.contains("gusion")) displayCombo = "Gusion";
            else if (currentCombo.contains("kadita")) displayCombo = "Kadita";
            else if (currentCombo.contains("beatrix")) displayCombo = "Beatrix ultimate";
            else if (currentCombo.contains("xavier")) displayCombo = "Xavier";
            else if (currentCombo.contains("selena")) displayCombo = "Selena";
            
            final TextView[] btnComboRef = new TextView[1];
            boolean comboLocked = FeatureManager.isFeatureLocked(prefs, "combo");
            
            btnComboRef[0] = (TextView) UIFactory.btn(ctx, "Select Hero: [" + displayCombo + "]", UIFactory.C_BTN_DRK, () -> {
                if (comboLocked) {
                    Toast.makeText(ctx, "🔒 VIP Only! Upgrade to use Hero Combo", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] comboList = {"None", "Gusion", "Kadita 1-2 petrify", "Beatrix ultimate", "Xavier 1-2", "Selena 2-1"};
                dialogShower.showModernDialog("SELECT HERO COMBO", comboList, selected -> {
                    String valueToSave = selected.equals("None") ? "none" : selected.toLowerCase();
                    prefs.edit().putString("selected_combo", valueToSave).apply();
                    if (btnComboRef[0] != null) btnComboRef[0].setText("Select Hero: [" + selected + "]");
                    NativeSender.sendConfigToCpp(prefs, authManager, realScreenW, realScreenH);
                });
            });
            
            if (comboLocked) {
                btnComboRef[0].setEnabled(false);
                btnComboRef[0].setAlpha(0.5f);
            }
            
            l.addView(btnComboRef[0]);
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "TARGET PRIORITY"));
            l.addView(UIFactory.radioRow(ctx, prefs, authManager, radar, realScreenW, realScreenH, "aimbot_target", new String[]{"Nearest", "Low HP", "Low HP %"}));
        }));

        t.addView(UIFactory.card(ctx, l -> {
            l.addView(UIFactory.secTitle(ctx, "DETECTION & PREDICTION"));
            l.addView(UIFactory.slider(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Aimbot FOV Range", "aimbot_fov", 0, 250, 200));
            l.addView(UIFactory.slider(ctx, prefs, authManager, radar, realScreenW, realScreenH, "Prediction Scale (Franco,selena,nova,moskov,xavier,layla)", "aimbot_predict", 0, 3, 1));
        }));

        return t;
    }

    public interface ModernDialogShower {
        void showModernDialog(String title, String[] items, com.overlay.ui.OverlayView.DialogCallback callback);
    }
}
