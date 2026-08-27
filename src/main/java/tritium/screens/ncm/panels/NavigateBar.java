package tritium.screens.ncm.panels;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import org.lwjgl.input.Keyboard;
import tritium.management.FontManager;
import tritium.ncm.api.CloudMusicApi;
import tritium.ncm.music.CloudMusic;
import tritium.ncm.music.dto.Music;
import tritium.ncm.music.dto.PlayList;
import tritium.rendering.TextureManager;
import tritium.rendering.animation.Interpolations;
import tritium.rendering.rendersystem.RenderSystem;
import tritium.rendering.texture.Textures;
import tritium.rendering.ui.container.Panel;
import tritium.rendering.ui.container.ScrollPanel;
import tritium.rendering.ui.widgets.*;
import tritium.screens.ncm.NCMPanel;
import tritium.screens.ncm.NCMScreen;
import tritium.utils.KeyboardUtils;
import tritium.utils.Location;
import tritium.utils.I18n;
import tritium.utils.json.JsonUtils;
import tritium.utils.other.multithreading.MultiThreadingUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * @author IzumiiKonata
 * Date: 2025/10/16 22:00
 */
public class NavigateBar extends NCMPanel {

    TextFieldWidget searchField = new TextFieldWidget(FontManager.pf14bold);
    ScrollPanel playlistPanel = new ScrollPanel();
    private final AtomicLong suggestionRequest = new AtomicLong();
    private final List<SearchSuggestion> searchSuggestions = new CopyOnWriteArrayList<>();
    private SearchSuggestionPanel suggestionPanel;

    public NavigateBar() {
        this.layout();
    }

    private void layout() {
        RectWidget bg = new RectWidget();
        this.addChild(bg);

        this.setBeforeRenderCallback(() -> {
            this.setBounds(NCMScreen.getInstance().getPanelWidth() * .15, NCMScreen.getInstance().getPanelHeight());
            this.setPosition(0, 0);

            bg.setMargin(0);
            bg.setColor(this.getColor(NCMScreen.ColorType.GENERIC_BACKGROUND));
            bg.setAlpha(0.9f);
        });

        this.setOnKeyTypedCallback((character, keyCode) -> {

            if (KeyboardUtils.isKeyComboCtrl(keyCode, Keyboard.KEY_F)) {
                this.searchField.setFocused(true);
                this.searchField.getTextField().selectAll();
                return true;
            }

            return false;
        });

        RoundedRectWidget searchBar = new RoundedRectWidget();
        RoundedRectWidget searchBarFocusAnimation = new RoundedRectWidget();

        this.addChild(searchBarFocusAnimation);
        this.addChild(searchBar);

        searchBarFocusAnimation.setBeforeRenderCallback(() -> {
            if (!searchField.isFocused()) {
                searchBarFocusAnimation.setAlpha(0);
            } else {
                searchBarFocusAnimation.setAlpha(Interpolations.interpolate(searchBarFocusAnimation.getAlpha(), 1f, .3f));
                searchBarFocusAnimation.setRadius(4);
                searchBarFocusAnimation.setColor(0xff780C17);
                searchBarFocusAnimation.setBounds(searchBar.getRelativeX(), searchBar.getRelativeY(), searchBar.getWidth(), searchBar.getHeight());
                searchBarFocusAnimation.expand(1 + 5 * (1 - searchBarFocusAnimation.getAlpha()));
            }
        });

        searchBar
//            .setShouldSetMouseCursor(true)
            .setBeforeRenderCallback(() -> {
                searchBar.setAlpha(1f);
                searchBar.setColor(0xFF5E5E5E);
                searchBar.setMargin(8);
                searchBar.setHeight(16);
                searchBar.setRadius(3.5);
            });

        RoundedRectWidget searchBarBg = new RoundedRectWidget();
        searchBar.addChild(searchBarBg);

        searchBarBg.setBeforeRenderCallback(() -> {
            searchBarBg.setMargin(.5);
            searchBarBg.setAlpha(.6f);
            searchBar.setColor(0xFF292727);
            searchBarBg.setRadius(searchBar.getRadius() - .5);
        });

        LabelWidget lblSearchIcon = new LabelWidget("K", FontManager.music18);
        searchBar.addChild(lblSearchIcon);

        lblSearchIcon.setBeforeRenderCallback(() -> {
            lblSearchIcon.setColor(hexColor(100, 100, 100));
            lblSearchIcon.centerVertically();
            lblSearchIcon.setPosition(lblSearchIcon.getRelativeY(), lblSearchIcon.getRelativeY());
        });

        searchBar.addChild(searchField);

        this.searchField.setPlaceholder(I18n.get("tritium-music.ui.search.placeholder"));
        this.searchField.setTextChangedCallback(this::requestSearchSuggestions);

        this.searchField.setOnKeyTypedCallback((character, keyCode) -> {
            if (this.searchField.isFocused()) {
                if (keyCode == Keyboard.KEY_ESCAPE)
                    this.searchField.setFocused(false);

                if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                    submitSearch(this.searchField.getText());
                }

                return true;
            }

            return false;
        });

        searchField.setBeforeRenderCallback(() -> {
            searchField.drawUnderline(false);
            searchField.setMargin(2);
            double xSpacing = lblSearchIcon.getRelativeX() + lblSearchIcon.getWidth() + 4;
            searchField.setBounds(xSpacing, searchField.getRelativeY(), searchField.getWidth() - xSpacing, searchField.getHeight());
            searchField.setColor(this.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            searchField.setDisabledTextColor(RenderSystem.reAlpha(this.getColor(NCMScreen.ColorType.PRIMARY_TEXT), .4f));
//            Rect.draw(searchField.getX(), searchField.getY(), searchField.getWidth(), searchField.getHeight(), 0x800090ff);
        });

        suggestionPanel = new SearchSuggestionPanel();
        suggestionPanel.setParent(this);
        suggestionPanel.setBeforeRenderCallback(() -> {
            suggestionPanel.setPosition(searchBar.getX(), searchBar.getY() + searchBar.getHeight() + 3);
            double availableWidth = NCMScreen.getInstance().getPanelWidth() - searchBar.getX() - 8;
            suggestionPanel.setBounds(suggestionPanel.preferredWidth(searchBar.getWidth(), availableWidth), suggestionPanel.preferredHeight());
        });

        this.addChild(playlistPanel);
        this.playlistPanel.setBeforeRenderCallback(() -> {
            this.playlistPanel.setMargin(0);
            this.playlistPanel.setPosition(this.playlistPanel.getRelativeX(), searchBar.getRelativeY() + searchBar.getHeight() + 8);
            this.playlistPanel.setBounds(this.playlistPanel.getWidth(), this.playlistPanel.getHeight() - searchBar.getHeight() - 16 - 32);
        });

        this.playlistPanel.setSpacing(4);

        LabelWidget lbl = new LabelWidget("Tritium Music", FontManager.pf14bold);
        lbl.setBeforeRenderCallback(() -> {
            lbl.setColor(Color.GRAY);
            lbl.setPosition(6, lbl.getRelativeY());
        });

        this.playlistPanel.addChild(lbl);

        {
            PlaylistItem item = new PlaylistItem("A", () -> 0xFFC30218, () -> I18n.get("tritium-music.ui.navigation.home"), () -> NCMScreen.getInstance().setCurrentPanel(new HomePanel()));

            item.setShouldOverrideMouseCursor(true);

            this.playlistPanel.addChild(item);
        }

        LabelWidget lblPlaylists = new LabelWidget(I18n.get("tritium-music.ui.navigation.my_playlists"), FontManager.pf14bold);
        lblPlaylists.setBeforeRenderCallback(() -> {
            lblPlaylists.setColor(Color.GRAY);
            lblPlaylists.setPosition(6, lblPlaylists.getRelativeY());
        });

        this.playlistPanel.addChild(lblPlaylists);

        List<PlayList> pl = CloudMusic.playLists;

        if (pl != null) {
            List<PlayList> playLists = pl.stream().filter(playList -> !playList.isSubscribed()).toList();
            for (int i = 0; i < playLists.size(); i++) {
                PlayList playList = playLists.get(i);
                PlaylistItem item = new PlaylistItem(i == 0 ? "C" : "D", Color.GRAY::getRGB, playList::getName, () -> NCMScreen.getInstance().setCurrentPanel(new PlaylistPanel(playList)));
                item.setShouldOverrideMouseCursor(true);

                this.playlistPanel.addChild(item);
            }
        }

        LabelWidget lblSubscribed = new LabelWidget(I18n.get("tritium-music.ui.navigation.subscribed_playlists"), FontManager.pf14bold);
        lblSubscribed.setBeforeRenderCallback(() -> {
            lblSubscribed.setColor(Color.GRAY);
            lblSubscribed.setPosition(6, lblSubscribed.getRelativeY());
        });

        this.playlistPanel.addChild(lblSubscribed);

        if (pl != null) {
            pl.stream().filter(PlayList::isSubscribed).forEach(playList -> {
                PlaylistItem item = new PlaylistItem("D", Color.GRAY::getRGB, playList::getName, () -> NCMScreen.getInstance().setCurrentPanel(new PlaylistPanel(playList)));
                item.setShouldOverrideMouseCursor(true);

                this.playlistPanel.addChild(item);
            });
        }

        RoundedImageWidget creatorAvatar = new RoundedImageWidget(this.getUserAvatarLocation(), 0, 0, 0, 0);
        this.addChild(creatorAvatar);
        creatorAvatar.fadeIn();
        creatorAvatar.setLinearFilter(true);

        this.loadAvatar();

        creatorAvatar.setBeforeRenderCallback(() -> {
            creatorAvatar.setBounds(16, 16);
            creatorAvatar.setPosition(12, this.getHeight() - 8 - creatorAvatar.getHeight());
            creatorAvatar.setRadius(7.25);
        });

        LabelWidget lblCreator = new LabelWidget(() -> CloudMusic.profile == null ? I18n.get("tritium-music.ui.account.not_logged_in") : CloudMusic.profile.getName(), FontManager.pf16bold);
        this.addChild(lblCreator);

        lblCreator.setBeforeRenderCallback(() -> {
            lblCreator.setPosition(creatorAvatar.getRelativeX() + creatorAvatar.getWidth() + 4, creatorAvatar.getRelativeY() + creatorAvatar.getHeight() * .5 - lblCreator.getHeight() * .5);
            lblCreator.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
        });
    }

    @Override
    public void onRender(double mouseX, double mouseY) {
        if (suggestionPanel != null) {
            suggestionPanel.setOpen(searchField.isFocused() && !searchSuggestions.isEmpty());
        }
    }

    private void requestSearchSuggestions(String text) {
        String query = text == null ? "" : text.trim();
        long request = suggestionRequest.incrementAndGet();
        if (query.isEmpty()) {
            updateSearchSuggestions(request, List.of());
            return;
        }
        CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS).execute(() -> {
            if (request != suggestionRequest.get()) return;
            MultiThreadingUtil.runAsync(() -> {
                try {
                    JsonObject response = CloudMusicApi.searchSuggest(query).toJsonObject();
                    List<SearchSuggestion> suggestions = parseSuggestions(response, query);
                    MultiThreadingUtil.runOnMainThread(() -> updateSearchSuggestions(request, suggestions));
                } catch (Exception e) {
                    MultiThreadingUtil.runOnMainThread(() -> updateSearchSuggestions(request, List.of()));
                }
            });
        });
    }

    private List<SearchSuggestion> parseSuggestions(JsonObject response, String query) {
        if (response == null || !response.has("result") || !response.get("result").isJsonObject()) return List.of();
        JsonObject result = response.getAsJsonObject("result");
        ArrayList<SearchSuggestion> values = new ArrayList<>();
        addSongSuggestions(values, result.getAsJsonArray("songs"));
        addArtistSuggestions(values, result.getAsJsonArray("artists"));
        addPlaylistSuggestions(values, result.getAsJsonArray("playlists"));
        addKeywordSuggestions(values, result.getAsJsonArray("allMatch"), query);
        return values.stream().distinct().limit(6).toList();
    }

    private void addSongSuggestions(List<SearchSuggestion> target, JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject song = element.getAsJsonObject();
            String title = text(song, "name");
            String artists = names(song.getAsJsonArray("artists"));
            if (!title.isBlank()) target.add(new SearchSuggestion(title, artists));
        }
    }

    private void addArtistSuggestions(List<SearchSuggestion> target, JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            String title = text(element.getAsJsonObject(), "name");
            if (!title.isBlank()) target.add(new SearchSuggestion(title, ""));
        }
    }

    private void addPlaylistSuggestions(List<SearchSuggestion> target, JsonArray array) {
        if (array == null) return;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject playlist = element.getAsJsonObject();
            String title = text(playlist, "name");
            String creator = playlist.has("creator") && playlist.get("creator").isJsonObject()
                    ? text(playlist.getAsJsonObject("creator"), "nickname") : "";
            if (!title.isBlank()) target.add(new SearchSuggestion(title, creator));
        }
    }

    private void addKeywordSuggestions(List<SearchSuggestion> target, JsonArray array, String query) {
        if (array == null) return;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            String keyword = text(element.getAsJsonObject(), "keyword");
            if (!keyword.isBlank() && !keyword.equalsIgnoreCase(query)) {
                target.add(new SearchSuggestion(keyword, ""));
            }
        }
    }

    private String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private String names(JsonArray array) {
        if (array == null) return "";
        ArrayList<String> names = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                String name = text(element.getAsJsonObject(), "name");
                if (!name.isBlank()) names.add(name);
            }
        }
        return String.join(" / ", names);
    }

    private void updateSearchSuggestions(long request, List<SearchSuggestion> suggestions) {
        if (request != suggestionRequest.get() || suggestionPanel == null) return;
        searchSuggestions.clear();
        searchSuggestions.addAll(suggestions);
        suggestionPanel.contentChanged();
    }

    public boolean handleSuggestionClick(double mouseX, double mouseY, int mouseButton) {
        if (suggestionPanel == null || !suggestionPanel.contains(mouseX, mouseY)) return false;
        SearchSuggestion suggestion = suggestionPanel.suggestionAt(mouseX, mouseY);
        if (suggestion != null && mouseButton == 0) {
            searchField.setText(suggestion.title());
            submitSearch(suggestion.title());
        }
        return true;
    }

    public void renderSuggestionOverlay(double mouseX, double mouseY) {
        if (suggestionPanel != null) suggestionPanel.renderWidget(mouseX, mouseY, 0);
    }

    private void submitSearch(String text) {
        String query = text == null ? "" : text.trim();
        if (query.isEmpty()) return;
        suggestionRequest.incrementAndGet();
        searchSuggestions.clear();
        searchField.setFocused(false);
        PlayList playList = JsonUtils.parse("{}", PlayList.class);
        playList.setSearchMode(true);
        playList.musics = new CopyOnWriteArrayList<>();
        PlaylistPanel panel = new PlaylistPanel(playList);
        NCMScreen.getInstance().setCurrentPanel(panel);
        this.playlistPanel.getChildren().forEach(child -> {
            if (child instanceof PlaylistItem item) item.setSelected(false);
        });
        MultiThreadingUtil.runAsync(() -> {
            List<Music> search = CloudMusic.search(query);
            MultiThreadingUtil.runOnMainThread(() -> panel.updateSearchResults(search));
        });
    }

    private record SearchSuggestion(String title, String detail) {
    }

    private final class SearchSuggestionPanel extends Panel {
        private static final double PADDING = 4;
        private static final double ROW_PADDING = 6;
        private boolean open;
        private float visibility;
        private final float[] hoverAnimations = new float[6];

        private SearchSuggestionPanel() {
            setHidden(true);
            setShouldOverrideMouseCursor(true);
        }

        private void setOpen(boolean open) {
            this.open = open;
            if (open) setHidden(false);
        }

        private void contentChanged() {
            if (!searchSuggestions.isEmpty()) setHidden(false);
        }

        @Override
        public void onRender(double mouseX, double mouseY) {
            visibility = Interpolations.interpolate(visibility, open ? 1f : 0f, open ? .38f : .25f);
            if (!open && visibility <= .01f) {
                visibility = 0f;
                setHidden(true);
                return;
            }
            float alpha = getAlpha() * visibility;
            roundedRect(getX(), getY(), getWidth(), getHeight(), 5, RenderSystem.reAlpha(0xFF202126, alpha * .98f));
            double itemY = getY() + PADDING;
            for (int i = 0; i < searchSuggestions.size(); i++) {
                SearchSuggestion suggestion = searchSuggestions.get(i);
                double itemHeight = rowHeight();
                boolean hovered = isHovered(mouseX, mouseY, getX() + 3, itemY, getWidth() - 6, itemHeight);
                hoverAnimations[i] = Interpolations.interpolate(hoverAnimations[i], hovered ? 1f : 0f, .32f);
                if (hoverAnimations[i] > .004f) {
                    roundedRect(getX() + 3, itemY, getWidth() - 6, itemHeight, 4,
                            RenderSystem.reAlpha(0xFF363840, alpha * hoverAnimations[i]));
                }
                double textX = getX() + 8;
                double availableWidth = getWidth() - 16;
                String title = FontManager.pf14bold.trim(suggestion.title(), availableWidth);
                double textY = itemY + (itemHeight - FontManager.pf14bold.getStringHeight(title)) * .5;
                FontManager.pf14bold.drawString(title, textX, textY, RenderSystem.reAlpha(0xFFF2F3F5, alpha));
                if (!suggestion.detail().isBlank()) {
                    double detailX = textX + FontManager.pf14bold.getStringWidthD(title) + FontManager.pf12.getStringWidthD("  ");
                    double remaining = Math.max(0, getX() + getWidth() - 8 - detailX);
                    FontManager.pf12.drawString(FontManager.pf12.trim(suggestion.detail(), remaining), detailX,
                            textY + (FontManager.pf14bold.getStringHeight(title) - FontManager.pf12.getStringHeight(suggestion.detail())) * .5,
                            RenderSystem.reAlpha(0xFFB5B7BD, alpha));
                }
                itemY += itemHeight;
            }
        }

        private SearchSuggestion suggestionAt(double mouseX, double mouseY) {
            if (visibility <= .1f || !contains(mouseX, mouseY)) return null;
            int index = (int) ((mouseY - getY() - PADDING) / rowHeight());
            return index >= 0 && index < searchSuggestions.size() ? searchSuggestions.get(index) : null;
        }

        private boolean contains(double mouseX, double mouseY) {
            return visibility > .1f && isHovered(mouseX, mouseY, getX(), getY(), getWidth(), getHeight());
        }

        private double preferredHeight() {
            return PADDING * 2 + rowHeight() * searchSuggestions.size();
        }

        private double preferredWidth(double minimumWidth, double maximumWidth) {
            double contentWidth = minimumWidth;
            for (SearchSuggestion suggestion : searchSuggestions) {
                double width = FontManager.pf14bold.getStringWidthD(suggestion.title());
                if (!suggestion.detail().isBlank()) width += FontManager.pf12.getStringWidthD("  " + suggestion.detail());
                contentWidth = Math.max(contentWidth, width + 16);
            }
            return Math.min(contentWidth, maximumWidth);
        }

        private double rowHeight() {
            return Math.max(FontManager.pf14bold.getHeight(), FontManager.pf12.getHeight()) + ROW_PADDING * 2;
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int mouseButton) {
        return false;
    }

    private void loadAvatar() {

        if (CloudMusic.profile == null) {
            return;
        }

        TextureManager textureManager = TextureManager.getInstance();
        Location avatarLoc = this.getUserAvatarLocation();
        if (textureManager.getTexture(avatarLoc) != null)
            return;

        Textures.downloadTextureAndLoadAsync(CloudMusic.profile.getAvatarUrl() + "?param=32y32", avatarLoc);
    }

    private Location getUserAvatarLocation() {
        if (CloudMusic.profile == null) {
            return null;
        }

        return CloudMusic.profile.getAvatarLocation();
    }

    @Override
    public void onInit() {

    }

    public static class PlaylistItem extends Panel {

        String icon;
        Supplier<Integer> iconColorSupplier;
        Supplier<String> label;
        Runnable onClick;
        RoundedRectWidget bg = new RoundedRectWidget();

        @Getter
        @Setter
        boolean selected = false;

        float hoverAnim = 0f;

        public PlaylistItem(String icon, Supplier<Integer> iconColorSupplier, Supplier<String> label, Runnable onClick) {
            this.icon = icon;
            this.iconColorSupplier = iconColorSupplier;
            this.label = label;
            this.onClick = onClick;

            this.setBeforeRenderCallback(() -> {
                this.setBounds(this.getParentWidth(), 16);
                this.setPosition(4, this.getRelativeY());
            });

            bg.setClickable(false);

            this.addChild(bg);
            this.bg.setBeforeRenderCallback(() -> {
                bg.setMargin(0);
                float target = selected ? 0.2f : (this.isHovering() ? 0.1f : 0f);
                hoverAnim = Interpolations.interpolate(hoverAnim, target, 0.3f);
                bg.setHidden(hoverAnim <= 0.004f);
                bg.setColor(Color.BLACK);
                bg.setAlpha(hoverAnim);
                bg.setRadius(4);
            });

            LabelWidget lblIcon = new LabelWidget(icon, FontManager.music18);
            this.addChild(lblIcon);
            lblIcon.setBeforeRenderCallback(() -> {
                lblIcon.setColor(iconColorSupplier.get());
                lblIcon.centerVertically();
                lblIcon.setPosition(8, lblIcon.getRelativeY()/* + .5*/);
            });

            lblIcon.setClickable(false);

            LabelWidget lbl = new LabelWidget(label, FontManager.pf14bold);
            this.addChild(lbl);

            lbl.setBeforeRenderCallback(() -> {
                lbl.centerVertically();
                lbl.setPosition(lblIcon.getRelativeX() + lblIcon.getWidth() + 4, lbl.getRelativeY());
                lbl.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
                lbl.setMaxWidth(this.getWidth() - 8 - lblIcon.getWidth() - 12);
            });

            lbl.setClickable(false);

            this.setOnClickCallback(((relativeX, relativeY, mouseButton) -> {

                if (mouseButton == 0) {
                    this.selected = true;
                    bg.setHidden(false);

                    this.onClick.run();

                    NCMScreen.getInstance().getPlaylistsPanel().playlistPanel.getChildren().stream()
                            .filter(it -> it instanceof PlaylistItem && it != this)
                            .forEach(it -> ((PlaylistItem) it).setSelected(false));
                }

                return true;
            }));
        }

    }
}
