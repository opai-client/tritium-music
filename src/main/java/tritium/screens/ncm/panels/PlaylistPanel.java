package tritium.screens.ncm.panels;

import org.lwjgl.input.Keyboard;
import tritium.management.FontManager;
import tritium.TritiumMusicExtension;
import tritium.ncm.music.CloudMusic;
import tritium.ncm.music.dto.Music;
import tritium.ncm.music.dto.PlayList;
import tritium.rendering.TextureManager;
import tritium.rendering.animation.Interpolations;
import tritium.rendering.rendersystem.RenderSystem;
import tritium.rendering.texture.Textures;
import tritium.rendering.ui.AbstractWidget;
import tritium.rendering.ui.container.Panel;
import tritium.rendering.ui.container.ScrollPanel;
import tritium.rendering.ui.widgets.*;
import tritium.screens.ncm.CoverflowOverlay;
import tritium.screens.ncm.NCMPanel;
import tritium.screens.ncm.NCMScreen;
import tritium.utils.KeyboardUtils;
import tritium.utils.I18n;
import tritium.utils.Location;
import tritium.utils.other.multithreading.MultiThreadingUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author IzumiiKonata
 * Date: 2025/10/17 18:42
 */
public class PlaylistPanel extends NCMPanel {

    public PlayList playList;

    public PlaylistPanel(PlayList playlist) {
        this.playList = playlist;
    }

    private TextFieldWidget tfSearch;
    private final ContextMenuWidget contextMenu = new ContextMenuWidget();
    private double tfOpenAnimation = 20;
    private ScrollPanel musicsPanel;
    private List<Music> loadedMusics = List.of();

    @Override
    public void onInit() {

        double musicsContainerOffsetY;

        if (!playList.isSearchMode()) {
            RoundedImageWidget cover = new RoundedImageWidget(this.playList.getCoverLocation(), 0, 0, 0, 0);

            cover.setPosition(24, 24);
            cover.setBounds(128, 128);
            cover.fadeIn();
            cover.setLinearFilter(true);

            this.addChild(cover);
            this.loadCover();

            cover.setBeforeRenderCallback(() -> cover.setRadius(4));

//        LabelWidget lblPlaylistName = new LabelWidget(playList.name, FontManager.pf);
            RoundedButtonWidget btnPlay = new RoundedButtonWidget(I18n.get("tritium-music.ui.playlist.play"), FontManager.pf16bold);
            this.addChild(btnPlay);

            btnPlay.setBeforeRenderCallback(() -> {
                btnPlay.setBounds(57, 17);
                btnPlay.setPosition(cover.getRelativeX() + cover.getWidth() + 12, cover.getRelativeY() + cover.getHeight() - btnPlay.getHeight());
                btnPlay.setRadius(3);
                btnPlay.setColor(0xFFd60017);
                btnPlay.setTextColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            });

            btnPlay.setOnClickCallback((relativeX, relativeY, mouseButton) -> {

                if (mouseButton == 0) {
                    playList.loadMusicsWithCallback(musics -> CloudMusic.play(musics, 0));
                }

                return true;
            });

            RoundedButtonWidget btnPlayRandomOrder = new RoundedButtonWidget(I18n.get("tritium-music.ui.playlist.shuffle"), FontManager.pf16bold);
            this.addChild(btnPlayRandomOrder);

            btnPlayRandomOrder.setBeforeRenderCallback(() -> {
                btnPlayRandomOrder.setBounds(57, 17);
                btnPlayRandomOrder.setPosition(cover.getRelativeX() + cover.getWidth() + 12 + btnPlay.getWidth() + 8, cover.getRelativeY() + cover.getHeight() - btnPlayRandomOrder.getHeight());
                btnPlayRandomOrder.setRadius(3);
                btnPlayRandomOrder.setColor(0xFFd60017);
                btnPlayRandomOrder.setTextColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            });

            btnPlayRandomOrder.setOnClickCallback((relativeX, relativeY, mouseButton) -> {

                if (mouseButton == 0) {
                    playList.loadMusicsWithCallback(musics -> {
                        ArrayList<Music> music = new ArrayList<>(musics);
                        Collections.shuffle(music);
                        CloudMusic.play(music, 0);
                    });
                }

                return true;
            });

            RoundedButtonWidget btnCoverflow = new RoundedButtonWidget("Coverflow", FontManager.pf16bold);
            this.addChild(btnCoverflow);

            btnCoverflow.setBeforeRenderCallback(() -> {
                btnCoverflow.setBounds(57, 17);
                btnCoverflow.setPosition(cover.getRelativeX() + cover.getWidth() + 12 + btnPlay.getWidth() + 8 + btnPlayRandomOrder.getWidth() + 8, cover.getRelativeY() + cover.getHeight() - btnCoverflow.getHeight());
                btnCoverflow.setRadius(3);
                btnCoverflow.setColor(0xFFd60017);
                btnCoverflow.setTextColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            });

            btnCoverflow.setOnClickCallback((relativeX, relativeY, mouseButton) -> {

                if (mouseButton == 0) {
                    api.displayScreen(CoverflowOverlay.byPlaylist(playList));
                }

                return true;
            });

            RoundedRectWidget searchBar = new RoundedRectWidget();
            this.addChild(searchBar);

            searchBar
                    .setShouldOverrideMouseCursor(true)
                    .setOnClickCallback((relativeX, relativeY, mouseButton) -> {
                        if (mouseButton == 0) {
                            if (!this.tfSearch.isFocused()) {
                                this.tfSearch.setFocused(true);
                                this.tfSearch.getTextField().lmbPressed = true;
                            }
                        }

                        return true;
                    })
                    .setBeforeRenderCallback(() -> {
                        tfOpenAnimation = Interpolations.interpolate(tfOpenAnimation, this.tfSearch.isFocused() ? 80 : 20, .3f);

                        this.tfSearch.setHidden(!this.tfSearch.isFocused() && tfOpenAnimation < 21);

                        searchBar
                                .setAlpha(1f)
                                .setColor(0xFF5E5E5E)
                                .setWidth(tfOpenAnimation)
                                .setHeight(btnCoverflow.getHeight())
                                .setRadius(7)
                                .setPosition(btnCoverflow.getRelativeX() + btnCoverflow.getWidth() + 8, btnCoverflow.getRelativeY());
                    });

            RoundedRectWidget searchBarBg = new RoundedRectWidget();
            searchBar.addChild(searchBarBg);
            searchBarBg
                    .setClickable(false)
                    .setBeforeRenderCallback(() -> {
                        searchBarBg
                                .setMargin(.5)
                                .setAlpha(.6f)
                                .setRadius(searchBar.getRadius() - .5);
                        searchBar.setColor(0xFF292727);
                    });

            LabelWidget lblSearchIcon = new LabelWidget("K", FontManager.music18);
            searchBar.addChild(lblSearchIcon);
            lblSearchIcon
                    .setClickable(false)
                    .setColor(hexColor(100, 100, 100))
                    .setBeforeRenderCallback(() -> lblSearchIcon
                            .centerVertically()
                            .setPosition(lblSearchIcon.getRelativeY(), lblSearchIcon.getRelativeY()));

            this.tfSearch = new TextFieldWidget(FontManager.pf14bold);
            searchBar.addChild(tfSearch);

            this.tfSearch.setOnKeyTypedCallback((character, keyCode) -> {
                if (this.tfSearch.isFocused()) {
                    if (keyCode == Keyboard.KEY_ESCAPE)
                        this.tfSearch.setFocused(false);


                    return true;
                }

                return false;
            });

            this.setOnKeyTypedCallback((character, keyCode) -> {

                if (KeyboardUtils.isKeyComboCtrl(keyCode, Keyboard.KEY_G)) {
                    this.tfSearch.setFocused(true);
                    this.tfSearch.getTextField().selectAll();
                    return true;
                }

                return false;
            });

            tfSearch.setBeforeRenderCallback(() -> {
                tfSearch.drawUnderline(false);
                tfSearch.setMargin(2);
                double xSpacing = lblSearchIcon.getRelativeX() + lblSearchIcon.getWidth() + 4;
                tfSearch.setBounds(xSpacing, tfSearch.getRelativeY(), tfSearch.getWidth() - xSpacing, tfSearch.getHeight());
                tfSearch.setColor(this.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
                tfSearch.setDisabledTextColor(RenderSystem.reAlpha(this.getColor(NCMScreen.ColorType.PRIMARY_TEXT), .4f));
            });

            addViewModeControls(btnCoverflow);

            RoundedImageWidget creatorAvatar = new RoundedImageWidget(this.playList.getCreator().getAvatarLocation(), 0, 0, 0, 0);
            this.addChild(creatorAvatar);
            creatorAvatar.fadeIn();
            creatorAvatar.setLinearFilter(true);

            this.loadAvatar();

            creatorAvatar.setBeforeRenderCallback(() -> {
                creatorAvatar.setBounds(16, 16);
                creatorAvatar.setPosition(cover.getRelativeX() + cover.getWidth() + 12, btnPlay.getRelativeY() - 6 - creatorAvatar.getHeight());
                creatorAvatar.setRadius(7.25);
            });

            LabelWidget lblCreator = new LabelWidget(playList.getCreator().getName(), FontManager.pf16bold);
            this.addChild(lblCreator);

            lblCreator.setBeforeRenderCallback(() -> {
                lblCreator.setPosition(creatorAvatar.getRelativeX() + creatorAvatar.getWidth() + 4, creatorAvatar.getRelativeY() + creatorAvatar.getHeight() * .5 - lblCreator.getHeight() * .5);
                lblCreator.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            });

            LabelWidget lblPlaylistInfo = new LabelWidget(this::getPlayListInfo, FontManager.pf12);
            this.addChild(lblPlaylistInfo);

            lblPlaylistInfo.setBeforeRenderCallback(() -> {
                lblPlaylistInfo.setPosition(cover.getRelativeX() + cover.getWidth() + 12, creatorAvatar.getRelativeY() - 8 - lblPlaylistInfo.getHeight());
                lblPlaylistInfo.setColor(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT));
            });

            LabelWidget lblPlaylistName = new LabelWidget(playList.getName(), FontManager.pf32);
            this.addChild(lblPlaylistName);

            lblPlaylistName.setBeforeRenderCallback(() -> {
                lblPlaylistName.setPosition(cover.getRelativeX() + cover.getWidth() + 12, lblPlaylistInfo.getRelativeY() - 4 - lblPlaylistName.getHeight());
                lblPlaylistName.setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT));
            });

            musicsContainerOffsetY = cover.getRelativeY() + cover.getHeight() + 24;
        } else {
            RoundedButtonWidget viewModeAnchor = new RoundedButtonWidget("", FontManager.pf14bold);
            viewModeAnchor.setHidden(true);
            viewModeAnchor.setBounds(36, 17);
            viewModeAnchor.setPosition(0, 18);
            this.addChild(viewModeAnchor);
            addViewModeControls(viewModeAnchor);
            musicsContainerOffsetY = 47;
        }

        Panel rwMusicsContainer = new Panel();

        this.addChild(rwMusicsContainer);

        rwMusicsContainer.setBeforeRenderCallback(() -> {
            rwMusicsContainer.setBounds(this.getWidth() - 36, this.getHeight() - (musicsContainerOffsetY));
            rwMusicsContainer.centerHorizontally();
            rwMusicsContainer.setPosition(rwMusicsContainer.getRelativeX(), musicsContainerOffsetY);
        });

        musicsPanel = new ScrollPanel();

        rwMusicsContainer.addChild(musicsPanel);
        applyViewMode();

        musicsPanel.setBeforeRenderCallback(() -> musicsPanel.setMargin(0));

        playList.loadMusicsWithCallback(musics -> {
            loadedMusics = List.copyOf(musics);
            rebuildMusicWidgets();
        });

        if (this.tfSearch != null) {
            this.tfSearch.setTextChangedCallback(text -> {
                filterMusics(text);
            });
        }
        this.addChild(contextMenu);
    }

    private void addViewModeControls(RoundedButtonWidget rowButton) {
        double controlWidth = 36;
        double controlSpacing = 6;
        double rightMargin = 24;
        RoundedButtonWidget btnListView = new RoundedButtonWidget(I18n.get("tritium-music.ui.playlist.list_view"), FontManager.pf14bold);
        RoundedButtonWidget btnGridView = new RoundedButtonWidget(I18n.get("tritium-music.ui.playlist.grid_view"), FontManager.pf14bold);
        this.addChild(btnListView, btnGridView);

        btnListView.setBeforeRenderCallback(() -> {
            boolean selected = !isGridView();
            btnListView.setBounds(controlWidth, rowButton.getHeight());
            btnListView.setPosition(this.getWidth() - rightMargin - controlWidth * 2 - controlSpacing, rowButton.getRelativeY());
            btnListView.setRadius(4);
            btnListView.setColor(selected ? 0xFFD60017 : NCMScreen.getColor(NCMScreen.ColorType.ELEMENT_BACKGROUND));
            btnListView.setTextColor(NCMScreen.getColor(selected ? NCMScreen.ColorType.PRIMARY_TEXT : NCMScreen.ColorType.SECONDARY_TEXT));
        });
        btnGridView.setBeforeRenderCallback(() -> {
            boolean selected = isGridView();
            btnGridView.setBounds(controlWidth, rowButton.getHeight());
            btnGridView.setPosition(this.getWidth() - rightMargin - controlWidth, rowButton.getRelativeY());
            btnGridView.setRadius(4);
            btnGridView.setColor(selected ? 0xFFD60017 : NCMScreen.getColor(NCMScreen.ColorType.ELEMENT_BACKGROUND));
            btnGridView.setTextColor(NCMScreen.getColor(selected ? NCMScreen.ColorType.PRIMARY_TEXT : NCMScreen.ColorType.SECONDARY_TEXT));
        });
        btnListView.setOnClickCallback((x, y, button) -> {
            if (button == 0) setGridView(false);
            return true;
        });
        btnGridView.setOnClickCallback((x, y, button) -> {
            if (button == 0) setGridView(true);
            return true;
        });
    }

    private boolean isGridView() {
        return "Grid".equals(TritiumMusicExtension.getInstance().tritiumMusic.playlistView.getValue());
    }

    private void setGridView(boolean grid) {
        TritiumMusicExtension.getInstance().tritiumMusic.playlistView.setValue(grid ? "Grid" : "List");
        rebuildMusicWidgets();
    }

    private void applyViewMode() {
        boolean grid = isGridView();
        musicsPanel.setAlignment(grid ? ScrollPanel.Alignment.VERTICAL_WITH_HORIZONTAL_FILL : ScrollPanel.Alignment.VERTICAL);
        musicsPanel.setSpacing(grid ? 12 : 0);
        musicsPanel.setVerticalSpacing(grid ? 2 : 0);
        musicsPanel.setContentPadding(grid ? 3 : 0);
    }

    private void rebuildMusicWidgets() {
        if (musicsPanel == null) return;
        applyViewMode();
        musicsPanel.getChildren().clear();
        musicsPanel.actualScrollOffset = 0;
        musicsPanel.targetScrollOffset = 0;
        MusicWidget.Style style = isGridView() ? MusicWidget.Style.GRID : MusicWidget.Style.LIST;
        long revealStart = System.currentTimeMillis();
        for (int i = 0; i < loadedMusics.size(); i++) {
            Music music = loadedMusics.get(i);
            int playlistIndex = playList.getMusics().indexOf(music);
            if (playlistIndex < 0) playlistIndex = i;
            musicsPanel.addChild(new MusicWidget(music, playList, playlistIndex, revealStart, this, style).setShouldOverrideMouseCursor(true));
        }
        filterMusics(tfSearch == null ? "" : tfSearch.getText());
    }

    private void filterMusics(String text) {
        if (musicsPanel == null) return;
        String query = text == null ? "" : text.toLowerCase();
        musicsPanel.getChildren().stream()
                .filter(child -> child instanceof MusicWidget)
                .map(child -> (MusicWidget) child)
                .forEach(widget -> widget.setHidden(!query.isEmpty() &&
                        !widget.music.getName().toLowerCase().contains(query) &&
                        !widget.music.getTranslatedNames().toLowerCase().contains(query) &&
                        widget.music.getArtists().stream().noneMatch(artist -> artist != null && artist.getName() != null && artist.getName().toLowerCase().contains(query)) &&
                        (widget.music.getAlbum() == null || widget.music.getAlbum().getName() == null || !widget.music.getAlbum().getName().toLowerCase().contains(query))));
    }

    public void openMusicMenu(MusicWidget widget, double mouseX, double mouseY) {
        List<ContextMenuWidget.Item> items = new ArrayList<>();
        boolean liked = CloudMusic.likeList != null && CloudMusic.likeList.contains(widget.music.getId());
        items.add(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.play"), () -> {
            int index = playList.getMusics().indexOf(widget.music);
            if (index >= 0) CloudMusic.play(playList.getMusics(), index);
        }));
        items.add(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.play_next"), () -> CloudMusic.playNext(widget.music)));
        items.add(new ContextMenuWidget.Item(I18n.get(liked ? "tritium-music.ui.menu.unlike" : "tritium-music.ui.menu.like"), () -> runLibraryOperation(() -> widget.music.setLike(!liked))));
        items.add(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.add_to_playlist"), () -> openAddToPlaylistMenu(widget, mouseX, mouseY)));
        items.add(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.copy_id"), () -> KeyboardUtils.setClipboardString(String.valueOf(widget.music.getId()))));
        if (!playList.isSearchMode()) {
            items.add(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.remove_from_playlist"), () -> removeMusic(widget)));
        }
        contextMenu.open(mouseX - getX(), mouseY - getY(), items);
    }

    private void openAddToPlaylistMenu(MusicWidget widget, double mouseX, double mouseY) {
        List<PlayList> playlists = CloudMusic.playLists == null
                ? List.of()
                : CloudMusic.playLists.stream().filter(candidate -> !candidate.isSubscribed()).toList();
        if (playlists.isEmpty()) {
            contextMenu.open(mouseX - getX(), mouseY - getY(), List.of(new ContextMenuWidget.Item(I18n.get("tritium-music.ui.menu.no_playlists"), null, false, false)));
            return;
        }
        contextMenu.open(mouseX - getX(), mouseY - getY(), playlists.stream()
                .map(target -> new ContextMenuWidget.Item(target.getName(), () -> runLibraryOperation(() -> target.addToList(widget.music.getId()))))
                .toList());
    }

    private void removeMusic(MusicWidget widget) {
        runLibraryOperation(() -> {
            playList.removeFromList(widget.music.getId());
            playList.getMusics().remove(widget.music);
            loadedMusics = List.copyOf(playList.getMusics());
            MultiThreadingUtil.runOnMainThread(this::rebuildMusicWidgets);
        });
    }

    private void runLibraryOperation(Runnable operation) {
        MultiThreadingUtil.runAsync(() -> {
            operation.run();
            CloudMusic.refreshLibrary();
            MultiThreadingUtil.runOnMainThread(() -> NCMScreen.getInstance().markDirty());
        });
    }

    @Override
    public void onMouseClickReceived(double mouseX, double mouseY, int mouseButton) {
        if (contextMenu.handleClick(mouseX, mouseY, mouseButton)) return;
        super.onMouseClickReceived(mouseX, mouseY, mouseButton);
    }

    public void onMouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (musicsPanel == null) return;
        boolean insidePanel = musicsPanel.isHovered(mouseX, mouseY, musicsPanel.getX(), musicsPanel.getY(), musicsPanel.getWidth(), musicsPanel.getHeight());
        musicsPanel.getChildren().stream()
                .filter(child -> child instanceof MusicWidget)
                .map(child -> (MusicWidget) child)
                .forEach(widget -> widget.onMouseReleased(mouseX, mouseY, mouseButton, insidePanel));
    }

    @Override
    public void renderWidget(double mouseX, double mouseY, int dWheel) {
        if (contextMenu.handleWheel(mouseX, mouseY, dWheel)) dWheel = 0;
        super.renderWidget(mouseX, mouseY, dWheel);
    }

    @Override
    protected boolean shouldRenderChildren(AbstractWidget<?> child, double mouseX, double mouseY) {
        return child != contextMenu && super.shouldRenderChildren(child, mouseX, mouseY);
    }

    public void renderContextMenuOverlay(double mouseX, double mouseY) {
        contextMenu.setAlpha(getAlpha());
        contextMenu.renderWidget(mouseX, mouseY, 0);
    }

    public void updateSearchResults(List<Music> musics) {
        playList.musics.clear();
        playList.musics.addAll(musics);
        loadedMusics = List.copyOf(musics);
        rebuildMusicWidgets();
    }

    private String formatDuration(long totalMillis) {
        long totalSeconds = totalMillis / 1000;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();

        if (hours > 0) {
            sb.append(I18n.get("tritium-music.ui.duration.hours", String.format("%02d", hours)));
        }

        if (minutes > 0) {
            sb.append(I18n.get("tritium-music.ui.duration.minutes", String.format("%02d", minutes)));
        }

        sb.append(I18n.get("tritium-music.ui.duration.seconds", String.format("%02d", seconds)));

        return sb.toString();
    }

    String cached = "";
    int lastSize = -1;

    private String getPlayListInfo() {
        if (!playList.musicsLoaded)
            return "";

        List<Music> musics = playList.musics;

        if (lastSize != musics.size()) {
            lastSize = musics.size();
            if (musics.isEmpty()) {
                cached = I18n.get("tritium-music.ui.playlist.song_count", playList.getCount());
            } else {
                cached = I18n.get("tritium-music.ui.playlist.song_count", musics.size()) + " · " + this.formatDuration(musics.stream().mapToLong(Music::getDuration).sum());
            }
        }

        return cached;
    }

    private void loadCover() {

        TextureManager textureManager = TextureManager.getInstance();
        Location coverLoc = this.playList.getCoverLocation();
        if (textureManager.getTexture(coverLoc) != null)
            return;

        Textures.downloadTextureAndLoadAsync(playList.getCoverUrl() + "?param=256y256", coverLoc);
    }

    private void loadAvatar() {
        TextureManager textureManager = TextureManager.getInstance();
        Location avatarLoc = this.playList.getCreator().getAvatarLocation();
        if (textureManager.getTexture(avatarLoc) != null)
            return;

        Textures.downloadTextureAndLoadAsync(playList.getCreator().getAvatarUrl() + "?param=32y32", avatarLoc);
    }

}
