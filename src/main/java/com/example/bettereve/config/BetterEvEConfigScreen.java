package com.example.bettereve.config;

import com.example.bettereve.config.BetterEvEConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class BetterEvEConfigScreen extends Screen {
    private final Screen lastScreen;
    private ConfigEntryList configEntryList;
    private Button doneButton;
    private boolean madeChanges = false;

    public BetterEvEConfigScreen(Screen lastScreen) {
        super(Component.literal("BetterEvE 配置"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        this.configEntryList = new ConfigEntryList(this, this.minecraft);
        this.addWidget(this.configEntryList);

        this.doneButton = new Button.Builder(CommonComponents.GUI_DONE, (button) -> {
            this.saveChanges();
            this.onClose();
        }).pos(this.width / 2 - 100, this.height - 29).size(200, 20).build();
        this.addRenderableWidget(this.doneButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        this.configEntryList.render(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 16777215);
        this.doneButton.setMessage(madeChanges ? Component.literal("保存并退出") : CommonComponents.GUI_DONE);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(lastScreen);
    }

    private void saveChanges() {
        if (madeChanges) {
            // 这里可以添加保存逻辑，如果需要的话
            madeChanges = false;
        }
    }

    public void setMadeChanges() {
        this.madeChanges = true;
    }

    private static class ConfigEntryList extends ContainerObjectSelectionList<ConfigEntry> {
        private final BetterEvEConfigScreen parent;
        private final Minecraft minecraft;
        private final List<ConfigEntry> entries;

        public ConfigEntryList(BetterEvEConfigScreen parent, Minecraft minecraft) {
            super(minecraft, parent.width, parent.height - 39, 32, parent.height - 32, 20);
            this.parent = parent;
            this.minecraft = minecraft;
            this.entries = new ArrayList<>();
            this.loadConfigEntries();
        }

        private void loadConfigEntries() {
            // 渲染系统配置
            entries.add(new ConfigEntry("是否显示实体百分比血量",
                    String.valueOf(BetterEvEConfig.INSTANCE.render_showHealthPercent.get()),
                    "boolean", this.parent));
            entries.add(new ConfigEntry("血量文本相对名称标签的Y轴偏移",
                    String.valueOf(BetterEvEConfig.INSTANCE.render_healthTextOffsetY.get()),
                    "double", this.parent));
            entries.add(new ConfigEntry("是否显示血量条",
                    String.valueOf(BetterEvEConfig.INSTANCE.render_showHealthBar.get()),
                    "boolean", this.parent));
            entries.add(new ConfigEntry("血量条宽度（像素）",
                    String.valueOf(BetterEvEConfig.INSTANCE.render_healthBarWidth.get()),
                    "double", this.parent));
            entries.add(new ConfigEntry("血量条高度（像素）",
                    String.valueOf(BetterEvEConfig.INSTANCE.render_healthBarHeight.get()),
                    "double", this.parent));
            entries.add(new ConfigEntry("血量条相对实体顶部的Y轴偏移",
                    String.valueOf(BetterEvEConfig.INSTANCE.render_healthBarOffsetY.get()),
                    "double", this.parent));

            // 寻敌系统配置
            entries.add(new ConfigEntry("全局默认寻敌范围（格）",
                    String.valueOf(BetterEvEConfig.INSTANCE.targeting_defaultFollowRange.get()),
                    "double", this.parent));

            // 水晶基地系统配置
            entries.add(new ConfigEntry("水晶基地默认血量",
                    String.valueOf(BetterEvEConfig.INSTANCE.crystal_defaultHealth.get()),
                    "int", this.parent));
            entries.add(new ConfigEntry("水晶基地清空地图范围（格）",
                    String.valueOf(BetterEvEConfig.INSTANCE.crystal_clearRadius.get()),
                    "int", this.parent));
            // 实体等级系统配置
            entries.add(new ConfigEntry("每级生命值加成比例（%）",
                    String.valueOf(BetterEvEConfig.INSTANCE.entitylv_healthBonus.get()),
                    "double", this.parent));
            entries.add(new ConfigEntry("每级攻击力加成比例（%）",
                    String.valueOf(BetterEvEConfig.INSTANCE.entitylv_attackBonus.get()),
                    "double", this.parent));
            entries.add(new ConfigEntry("等级上限",
                    String.valueOf(BetterEvEConfig.INSTANCE.entitylv_maxLevel.get()),
                    "int", this.parent));


            // 全局开关配置
            entries.add(new ConfigEntry("全局HUD显示总开关",
                    String.valueOf(BetterEvEConfig.INSTANCE.global_hudEnabled.get()),
                    "boolean", this.parent));
            entries.add(new ConfigEntry("全局等级系统总开关",
                    String.valueOf(BetterEvEConfig.INSTANCE.global_levelEnabled.get()),
                    "boolean", this.parent));

            for (ConfigEntry entry : entries) {
                this.addEntry(entry);
            }
        }
    }

    private static class ConfigEntry extends ContainerObjectSelectionList.Entry<ConfigEntry> {
        private final String key;
        private String value;
        private final String type;
        private EditBox editBox;
        private Button booleanButton;
        private final BetterEvEConfigScreen parent;

        public ConfigEntry(String key, String value, String type, BetterEvEConfigScreen parent) {
            this.key = key;
            this.value = value;
            this.type = type;
            this.parent = parent;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTicks) {
            guiGraphics.drawString(Minecraft.getInstance().font,
                    Component.literal(key),
                    left + 5, top + 6, 0xFFFFFF);

            if (type.equals("boolean")) {
                if (booleanButton == null) {
                    booleanButton = new Button.Builder(Component.literal(getBooleanButtonText()), (button) -> {
                        this.toggleBooleanValue();
                    }).pos(left + width / 2, top + 5).size(100, 18).build();
                }
                // 每次渲染时更新按钮位置
                booleanButton.setPosition(left + width / 2, top + 5);
                booleanButton.setMessage(Component.literal(getBooleanButtonText()));
                booleanButton.render(guiGraphics, mouseX, mouseY, partialTicks);
            } else {
                if (editBox == null) {
                    editBox = new EditBox(Minecraft.getInstance().font, left + width / 2, top + 5, width / 2 - 10, 18,
                            Component.literal(key));
                    editBox.setValue(value);
                    editBox.setMaxLength(100);
                    editBox.setResponder((newValue) -> {
                        this.value = newValue;
                        this.parent.setMadeChanges();
                    });
                }
                // 每次渲染时更新文本框位置
                editBox.setPosition(left + width / 2, top + 5);
                editBox.render(guiGraphics, mouseX, mouseY, partialTicks);
            }
        }

        private String getBooleanButtonText() {
            return Boolean.parseBoolean(value) ? "是" : "否";
        }

        private void toggleBooleanValue() {
            this.value = String.valueOf(!Boolean.parseBoolean(value));
            this.parent.setMadeChanges();
            this.updateConfigValue();
        }

        private void updateConfigValue() {
            // 根据配置项类型更新实际配置
            // 这里需要根据 key 来确定要更新哪个配置项
            switch (key) {
                case "是否显示实体百分比血量":
                    BetterEvEConfig.INSTANCE.render_showHealthPercent.set(Boolean.parseBoolean(value));
                    break;
                case "全局HUD显示总开关":
                    BetterEvEConfig.INSTANCE.global_hudEnabled.set(Boolean.parseBoolean(value));
                    break;
                case "全局等级系统总开关":
                    BetterEvEConfig.INSTANCE.global_levelEnabled.set(Boolean.parseBoolean(value));
                    break;
                case "血量文本相对名称标签的Y轴偏移":
                    BetterEvEConfig.INSTANCE.render_healthTextOffsetY.set(Double.parseDouble(value));
                    break;
                case "是否显示血量条":
                    BetterEvEConfig.INSTANCE.render_showHealthBar.set(Boolean.parseBoolean(value));
                    break;
                case "血量条宽度（像素）":
                    BetterEvEConfig.INSTANCE.render_healthBarWidth.set(Double.parseDouble(value));
                    break;
                case "血量条高度（像素）":
                    BetterEvEConfig.INSTANCE.render_healthBarHeight.set(Double.parseDouble(value));
                    break;
                case "血量条相对实体顶部的Y轴偏移":
                    BetterEvEConfig.INSTANCE.render_healthBarOffsetY.set(Double.parseDouble(value));
                    break;
                case "全局默认寻敌范围（格）":
                    BetterEvEConfig.INSTANCE.targeting_defaultFollowRange.set(Double.parseDouble(value));
                    break;
                case "水晶基地默认血量":
                    BetterEvEConfig.INSTANCE.crystal_defaultHealth.set(Integer.parseInt(value));
                    break;
                case "水晶基地清空地图范围（格）":
                    BetterEvEConfig.INSTANCE.crystal_clearRadius.set(Integer.parseInt(value));
                    break;
                case "每级生命值加成比例（%）":
                    BetterEvEConfig.INSTANCE.entitylv_healthBonus.set(Double.parseDouble(value));
                    break;
                case "每级攻击力加成比例（%）":
                    BetterEvEConfig.INSTANCE.entitylv_attackBonus.set(Double.parseDouble(value));
                    break;
                case "等级上限":
                    BetterEvEConfig.INSTANCE.entitylv_maxLevel.set(Integer.parseInt(value));
                    break;

            }
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.AbstractWidget> children() {
            List<net.minecraft.client.gui.components.AbstractWidget> list = new ArrayList<>();
            if (type.equals("boolean") && booleanButton != null) {
                list.add(booleanButton);
            } else if (editBox != null) {
                list.add(editBox);
            }
            return list;
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return new ArrayList<>();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (type.equals("boolean") && booleanButton != null && booleanButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            } else if (editBox != null && editBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return false;
        }
    }
}