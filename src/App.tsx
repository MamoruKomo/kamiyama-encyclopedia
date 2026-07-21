import * as Location from 'expo-location';
import { Bell, BookOpen, Home, Leaf, MapPinned, Sparkles } from 'lucide-react-native';
import { cloneElement, useEffect, useMemo, useState, type ReactElement } from 'react';
import { Pressable, SafeAreaView, StatusBar, Text, View } from 'react-native';

import { EncyclopediaPanel } from './components/EncyclopediaPanel';
import { HomePanel } from './components/HomePanel';
import { MapPanel } from './components/MapPanel';
import { ReviewPanel } from './components/ReviewPanel';
import { KAMIYAMA_CENTER } from './data/kamiyama';
import { confirmReviewObservation, pullReviewObservations, type ReviewObservation } from './lib/reviewApi';
import { deleteObservation, loadObservations, saveObservation } from './lib/storage';
import { configureSyncEndpointFromUrl, pullThinkletObservations } from './lib/syncApi';
import {
  clearThinkletObservationParam,
  hasThinkletObservationParam,
  readThinkletObservationFromUrl,
} from './lib/thinkletImport';
import { colors, shadow } from './styles/theme';
import type { LatLng, Observation } from './types/domain';

type Tab = 'home' | 'encyclopedia' | 'map' | 'review';

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('home');
  const [observations, setObservations] = useState<Observation[]>([]);
  const [currentLocation, setCurrentLocation] = useState<LatLng | null>(null);
  const [selectedObservation, setSelectedObservation] = useState<Observation | null>(null);
  const [reviewObservations, setReviewObservations] = useState<ReviewObservation[]>([]);
  const [isReviewLoading, setIsReviewLoading] = useState(false);
  const [statusText, setStatusText] = useState('新しい観察を待っています。');

  useEffect(() => {
    const configuredSyncEndpoint = configureSyncEndpointFromUrl();
    const hasIncomingThinkletObservation = hasThinkletObservationParam();
    loadObservations()
      .then(async (loadedObservations) => {
        let nextObservations = loadedObservations;
        const thinkletObservation = readThinkletObservationFromUrl();
        if (thinkletObservation) {
          const alreadyImported = nextObservations.some((item) => item.id === thinkletObservation.id);
          if (!alreadyImported) {
            await saveObservation(thinkletObservation);
            nextObservations = [thinkletObservation, ...nextObservations];
            setStatusText('THINKLETから新しい観察が届きました。');
          } else {
            setStatusText('この観察はすでに受け取り済みです。');
          }
          setSelectedObservation(thinkletObservation);
          setCurrentLocation({ latitude: thinkletObservation.latitude, longitude: thinkletObservation.longitude });
          clearThinkletObservationParam();
        }

        const syncedObservations = await pullThinkletObservations();
        const uniqueSynced = syncedObservations.filter(
          (synced) => !nextObservations.some((item) => item.id === synced.id),
        );
        if (uniqueSynced.length > 0) {
          await Promise.all(uniqueSynced.map(saveObservation));
          nextObservations = [...uniqueSynced, ...nextObservations];
          setSelectedObservation(uniqueSynced[0]);
          setCurrentLocation({ latitude: uniqueSynced[0].latitude, longitude: uniqueSynced[0].longitude });
          if (!thinkletObservation) setStatusText(`${uniqueSynced.length}件の発見を受け取りました。`);
        } else if (configuredSyncEndpoint && !thinkletObservation) {
          setStatusText('THINKLETから自動で受け取る準備ができています。');
        }
        setObservations(nextObservations);
      })
      .catch(() => setStatusText('通信を確認しています。届いた写真はあとから自動で表示されます。'));
    refreshReviewObservations();
    resolveInitialLocation({ preserveStatus: hasIncomingThinkletObservation });
  }, []);

  const discoveredSpeciesCount = useMemo(
    () => new Set(observations.map((item) => item.candidateId).filter(Boolean)).size,
    [observations],
  );

  async function resolveInitialLocation(options?: { preserveStatus?: boolean }) {
    const permission = await Location.requestForegroundPermissionsAsync();
    if (permission.status !== 'granted') {
      setCurrentLocation(KAMIYAMA_CENTER);
      if (!options?.preserveStatus) setStatusText('神山町の中心から地図を表示しています。');
      return;
    }
    try {
      const location = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setCurrentLocation({ latitude: location.coords.latitude, longitude: location.coords.longitude });
      if (!options?.preserveStatus) setStatusText('現在地を取得しました。THINKLETから自動で受け取れます。');
    } catch {
      setCurrentLocation(KAMIYAMA_CENTER);
      if (!options?.preserveStatus) setStatusText('神山町の中心から地図を表示しています。');
    }
  }

  async function handleDeleteObservation(id: string) {
    await deleteObservation(id);
    setObservations((current) => current.filter((item) => item.id !== id));
    if (selectedObservation?.id === id) setSelectedObservation(null);
  }

  async function refreshReviewObservations() {
    setIsReviewLoading(true);
    try {
      const reviews = await pullReviewObservations();
      setReviewObservations(reviews);
      if (reviews.length > 0) setStatusText(`${reviews.length}件のAI候補が届いています。`);
    } catch {
      setStatusText('AI候補を確認できませんでした。通信が戻ったらもう一度試せます。');
    } finally {
      setIsReviewLoading(false);
    }
  }

  async function handleConfirmReview(observation: ReviewObservation, speciesId: string) {
    try {
      const confirmed = await confirmReviewObservation(observation, speciesId);
      await saveObservation(confirmed);
      setObservations((current) => (
        current.some((item) => item.id === confirmed.id) ? current : [confirmed, ...current]
      ));
      setReviewObservations((current) => current.filter((item) => item.id !== observation.id));
      setSelectedObservation(confirmed);
      setCurrentLocation({ latitude: confirmed.latitude, longitude: confirmed.longitude });
      setActiveTab('encyclopedia');
      setStatusText(`${confirmed.customName}を図鑑に登録しました。`);
    } catch {
      setStatusText('図鑑に登録できませんでした。通信を確認してもう一度試してください。');
    }
  }

  function openMap(observation?: Observation) {
    if (observation) setSelectedObservation(observation);
    setActiveTab('map');
  }

  const screenTitle = {
    home: '神山図鑑',
    encyclopedia: '図鑑',
    map: '観察マップ',
    review: 'AI候補',
  }[activeTab];

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar barStyle="dark-content" backgroundColor={colors.paper} />
      <View style={styles.appFrame}>
        <View style={styles.topBar}>
          <View style={styles.brandMark}>
            <Leaf color={colors.white} size={20} fill={colors.white} />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.topTitle}>{screenTitle}</Text>
            {activeTab === 'home' ? (
              <Text style={styles.topSubtitle}>{discoveredSpeciesCount}種類を発見</Text>
            ) : null}
          </View>
          <Pressable onPress={() => setActiveTab('review')} style={styles.iconButton} accessibilityLabel="AI候補を開く">
            <Bell color={colors.ink} size={21} />
            {reviewObservations.length > 0 ? (
              <View style={styles.notificationBadge}>
                <Text style={styles.notificationText}>{reviewObservations.length}</Text>
              </View>
            ) : null}
          </Pressable>
        </View>

        <View style={styles.content}>
          {activeTab === 'home' ? (
            <HomePanel
              observations={observations}
              reviewCount={reviewObservations.length}
              statusText={statusText}
              onOpenMap={openMap}
              onOpenEncyclopedia={() => setActiveTab('encyclopedia')}
              onOpenReview={() => setActiveTab('review')}
            />
          ) : null}

          {activeTab === 'encyclopedia' ? (
            <EncyclopediaPanel observations={observations} onDeleteObservation={handleDeleteObservation} />
          ) : null}

          {activeTab === 'map' ? (
            <View style={styles.mapContent}>
              <MapPanel
                observations={observations}
                currentLocation={currentLocation}
                onSelectObservation={setSelectedObservation}
              />
              {selectedObservation ? (
                <Pressable style={styles.mapSelection} onPress={() => setActiveTab('encyclopedia')}>
                  <View style={styles.mapSelectionDot} />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.mapSelectionName}>{selectedObservation.customName}</Text>
                    <Text style={styles.mapSelectionMeta} numberOfLines={1}>
                      {new Date(selectedObservation.observedAt).toLocaleString('ja-JP')} ・ {selectedObservation.environment}
                    </Text>
                  </View>
                  <BookOpen color={colors.forest} size={20} />
                </Pressable>
              ) : null}
            </View>
          ) : null}

          {activeTab === 'review' ? (
            <ReviewPanel
              reviews={reviewObservations}
              isLoading={isReviewLoading}
              onRefresh={refreshReviewObservations}
              onConfirm={handleConfirmReview}
            />
          ) : null}
        </View>

        <View style={styles.tabBar}>
          <TabButton label="ホーム" icon={<Home size={22} />} active={activeTab === 'home'} onPress={() => setActiveTab('home')} />
          <TabButton label="図鑑" icon={<BookOpen size={22} />} active={activeTab === 'encyclopedia'} onPress={() => setActiveTab('encyclopedia')} />
          <TabButton label="地図" icon={<MapPinned size={22} />} active={activeTab === 'map'} onPress={() => setActiveTab('map')} />
          <TabButton label="AI候補" icon={<Sparkles size={22} />} active={activeTab === 'review'} onPress={() => setActiveTab('review')} badge={reviewObservations.length} />
        </View>
      </View>
    </SafeAreaView>
  );
}

function TabButton({ label, icon, active, onPress, badge = 0 }: { label: string; icon: ReactElement<{ color?: string }>; active: boolean; onPress: () => void; badge?: number }) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => [styles.tabButton, pressed && styles.pressed]}>
      <View style={styles.tabIconWrap}>
        <View>{cloneElement(icon, { color: active ? colors.forest : colors.inkSoft })}</View>
        {badge > 0 ? <View style={styles.tabBadge}><Text style={styles.tabBadgeText}>{badge}</Text></View> : null}
      </View>
      <Text style={[styles.tabLabel, active && styles.tabLabelActive]}>{label}</Text>
      {active ? <View style={styles.activeLine} /> : null}
    </Pressable>
  );
}

const styles = {
  screen: { flex: 1, backgroundColor: '#E7ECE6' },
  appFrame: { flex: 1, width: '100%' as const, maxWidth: 720, alignSelf: 'center' as const, backgroundColor: colors.paper, ...shadow },
  topBar: { minHeight: 70, paddingHorizontal: 18, flexDirection: 'row' as const, alignItems: 'center' as const, gap: 11, backgroundColor: colors.paper, borderBottomWidth: 1, borderBottomColor: colors.line },
  brandMark: { width: 38, height: 38, borderRadius: 8, backgroundColor: colors.forest, alignItems: 'center' as const, justifyContent: 'center' as const },
  topTitle: { color: colors.ink, fontSize: 20, fontWeight: '900' as const },
  topSubtitle: { color: colors.inkSoft, fontSize: 10, marginTop: 1, fontWeight: '700' as const },
  iconButton: { width: 40, height: 40, borderRadius: 8, alignItems: 'center' as const, justifyContent: 'center' as const, borderWidth: 1, borderColor: colors.line, backgroundColor: colors.white },
  notificationBadge: { position: 'absolute' as const, right: -3, top: -4, minWidth: 18, height: 18, borderRadius: 9, paddingHorizontal: 4, alignItems: 'center' as const, justifyContent: 'center' as const, backgroundColor: colors.amber },
  notificationText: { color: colors.white, fontSize: 10, fontWeight: '900' as const },
  content: { flex: 1, backgroundColor: colors.paper },
  mapContent: { flex: 1, padding: 14, gap: 10 },
  mapSelection: { minHeight: 72, borderRadius: 8, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.line, padding: 13, flexDirection: 'row' as const, alignItems: 'center' as const, gap: 11, ...shadow },
  mapSelectionDot: { width: 12, height: 12, borderRadius: 6, backgroundColor: colors.forest },
  mapSelectionName: { color: colors.ink, fontSize: 15, fontWeight: '900' as const },
  mapSelectionMeta: { color: colors.inkSoft, fontSize: 11, marginTop: 4 },
  tabBar: { minHeight: 76, paddingHorizontal: 7, paddingTop: 6, paddingBottom: 7, flexDirection: 'row' as const, backgroundColor: colors.white, borderTopWidth: 1, borderTopColor: colors.line },
  tabButton: { flex: 1, minHeight: 60, alignItems: 'center' as const, justifyContent: 'center' as const, gap: 3, position: 'relative' as const },
  tabIconWrap: { position: 'relative' as const },
  tabLabel: { color: colors.inkSoft, fontSize: 10, fontWeight: '700' as const },
  tabLabelActive: { color: colors.forest, fontWeight: '900' as const },
  activeLine: { position: 'absolute' as const, bottom: 0, width: 28, height: 3, borderRadius: 2, backgroundColor: colors.forest },
  tabBadge: { position: 'absolute' as const, right: -10, top: -7, minWidth: 17, height: 17, paddingHorizontal: 4, borderRadius: 9, backgroundColor: colors.amber, alignItems: 'center' as const, justifyContent: 'center' as const },
  tabBadgeText: { color: colors.white, fontSize: 9, fontWeight: '900' as const },
  pressed: { opacity: 0.68 },
};
