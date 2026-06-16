import * as Location from 'expo-location';
import { useEffect, useMemo, useState } from 'react';
import { SafeAreaView, StatusBar, Text, Pressable, View } from 'react-native';

import { CapturePanel } from './components/CapturePanel';
import { EncyclopediaPanel } from './components/EncyclopediaPanel';
import { MapPanel } from './components/MapPanel';
import { KAMIYAMA_CENTER, speciesCandidates } from './data/kamiyama';
import { deleteObservation, loadObservations, saveObservation } from './lib/storage';
import { configureSyncEndpointFromUrl, pullThinkletObservations } from './lib/syncApi';
import {
  clearThinkletObservationParam,
  hasThinkletObservationParam,
  readThinkletObservationFromUrl,
} from './lib/thinkletImport';
import type { LatLng, Observation } from './types/domain';

type Tab = 'map' | 'capture' | 'encyclopedia';

const rarityLabel = {
  common: 'COMMON',
  uncommon: 'UNCOMMON',
  rare: 'RARE',
  special: 'SPECIAL',
} as const;

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('map');
  const [observations, setObservations] = useState<Observation[]>([]);
  const [currentLocation, setCurrentLocation] = useState<LatLng | null>(null);
  const [selectedObservation, setSelectedObservation] = useState<Observation | null>(null);
  const [statusText, setStatusText] = useState('神山町周辺の軽い範囲で探索を開始できます。');

  useEffect(() => {
    const configuredSyncEndpoint = configureSyncEndpointFromUrl();
    const hasIncomingThinkletObservation = hasThinkletObservationParam();
    loadObservations()
      .then(async (loadedObservations) => {
        let nextObservations = loadedObservations;
        const thinkletObservation = readThinkletObservationFromUrl();
        if (thinkletObservation) {
          const alreadyImported = nextObservations.some(
            (observation) => observation.id === thinkletObservation.id,
          );
          if (!alreadyImported) {
            await saveObservation(thinkletObservation);
            nextObservations = [thinkletObservation, ...nextObservations];
            setStatusText('THINKLETから観察を取り込みました。');
          } else {
            setStatusText('THINKLET観察はすでに取り込み済みです。');
          }
          setSelectedObservation(thinkletObservation);
          setCurrentLocation({
            latitude: thinkletObservation.latitude,
            longitude: thinkletObservation.longitude,
          });
          clearThinkletObservationParam();
        }

        const syncedObservations = await pullThinkletObservations();
        const uniqueSynced = syncedObservations.filter(
          (synced) => !nextObservations.some((observation) => observation.id === synced.id),
        );
        if (uniqueSynced.length > 0) {
          await Promise.all(uniqueSynced.map(saveObservation));
          nextObservations = [...uniqueSynced, ...nextObservations];
          setSelectedObservation(uniqueSynced[0]);
          setCurrentLocation({
            latitude: uniqueSynced[0].latitude,
            longitude: uniqueSynced[0].longitude,
          });
          if (!thinkletObservation) {
            setStatusText(`同期APIから${uniqueSynced.length}件のTHINKLET観察を取り込みました。`);
          }
        } else if (configuredSyncEndpoint && !thinkletObservation) {
          setStatusText('同期APIを設定しました。次回からTHINKLET観察を自動取り込みします。');
        }

        setObservations(nextObservations);
      })
      .catch((error) => setStatusText(`図鑑の読み込み/同期に失敗しました: ${String(error)}`));
    resolveInitialLocation({ preserveStatus: hasIncomingThinkletObservation });
  }, []);

  const discoveredCandidateIds = useMemo(
    () => new Set(observations.map((item) => item.candidateId).filter(Boolean)),
    [observations],
  );
  const rareObservationCount = useMemo(
    () => observations.filter((item) => item.rarity === 'rare' || item.rarity === 'special').length,
    [observations],
  );
  const progressPercent = Math.min(
    100,
    Math.round((discoveredCandidateIds.size / speciesCandidates.length) * 100),
  );
  const fieldRank = observations.length >= 12 ? 'MASTER' : observations.length >= 6 ? 'ACE' : 'ROOKIE';

  async function resolveInitialLocation(options?: { preserveStatus?: boolean }) {
    const permission = await Location.requestForegroundPermissionsAsync();
    if (permission.status !== 'granted') {
      setCurrentLocation(KAMIYAMA_CENTER);
      if (!options?.preserveStatus) {
        setStatusText('位置情報が未許可のため、神山町中心部から表示しています。');
      }
      return;
    }

    try {
      const location = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      setCurrentLocation({
        latitude: location.coords.latitude,
        longitude: location.coords.longitude,
      });
      if (!options?.preserveStatus) {
        setStatusText('現在地を取得しました。撮影すると発見ピンが作られます。');
      }
    } catch {
      setCurrentLocation(KAMIYAMA_CENTER);
      if (!options?.preserveStatus) {
        setStatusText('現在地を取得できないため、神山町中心部から表示しています。');
      }
    }
  }

  async function handleObservationSaved(observation: Observation) {
    await saveObservation(observation);
    setObservations((current) => [observation, ...current]);
    setSelectedObservation(observation);
    setActiveTab('map');
  }

  async function handleDeleteObservation(id: string) {
    await deleteObservation(id);
    setObservations((current) => current.filter((item) => item.id !== id));
    if (selectedObservation?.id === id) {
      setSelectedObservation(null);
    }
  }

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar barStyle="light-content" />
      <View style={styles.appFrame}>
        <View style={styles.header}>
          <View style={styles.headerGlow} />
          <View style={{ flex: 1, gap: 8 }}>
            <View>
              <Text style={styles.kicker}>KAMIYAMA FIELD GUIDE</Text>
              <Text style={styles.title}>神山生物図鑑</Text>
              <Text style={styles.subtitle}>歩いて、撮って、発見ピンを増やす</Text>
            </View>
            <View style={styles.progressTrack}>
              <View style={[styles.progressFill, { width: `${progressPercent}%` }]} />
            </View>
          </View>
          <View style={styles.progressBadge}>
            <Text style={styles.progressValue}>
              {discoveredCandidateIds.size}/{speciesCandidates.length}
            </Text>
            <Text style={styles.progressLabel}>DISCOVERED</Text>
          </View>
        </View>

        <View style={styles.hudRow}>
          <HudStat label="RANK" value={fieldRank} />
          <HudStat label="観察" value={observations.length.toString()} />
          <HudStat label="レア" value={rareObservationCount.toString()} accent />
        </View>

        <View style={styles.statusBar}>
          <Text style={styles.statusText}>{statusText}</Text>
        </View>

        <View style={styles.content}>
          {activeTab === 'map' ? (
            <View style={{ gap: 12 }}>
              <MapPanel
                observations={observations}
                currentLocation={currentLocation}
                onSelectObservation={setSelectedObservation}
              />
              {selectedObservation ? (
                <View style={[styles.selectionPanel, styles.selectionPanelActive]}>
                  <View style={styles.selectionHeader}>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.selectionEyebrow}>DISCOVERY LOCKED</Text>
                      <Text style={styles.selectionTitle}>{selectedObservation.customName}</Text>
                    </View>
                    <Text style={styles.rarityMedal}>{rarityLabel[selectedObservation.rarity]}</Text>
                  </View>
                  <Text style={styles.selectionText}>
                    {new Date(selectedObservation.observedAt).toLocaleString('ja-JP')}
                  </Text>
                  <Text style={styles.selectionText}>{selectedObservation.environment}</Text>
                </View>
              ) : (
                <View style={styles.selectionPanel}>
                  <Text style={styles.selectionEyebrow}>FIELD RADAR</Text>
                  <Text style={styles.selectionTitle}>探索マップ</Text>
                  <Text style={styles.selectionText}>
                    色つきエリアは自然環境の目安です。薄い既知ピンを起点に歩き、濃い発見ピンで自分だけの分布図を育てます。
                  </Text>
                </View>
              )}
            </View>
          ) : null}

          {activeTab === 'capture' ? (
            <CapturePanel
              currentLocation={currentLocation}
              onObservationSaved={handleObservationSaved}
              onLocationResolved={setCurrentLocation}
            />
          ) : null}

          {activeTab === 'encyclopedia' ? (
            <EncyclopediaPanel
              observations={observations}
              onDeleteObservation={handleDeleteObservation}
            />
          ) : null}
        </View>

        <View style={styles.tabBar}>
          <TabButton
            label="地図"
            icon="MAP"
            active={activeTab === 'map'}
            onPress={() => setActiveTab('map')}
          />
          <TabButton
            label="撮影"
            icon="SCAN"
            active={activeTab === 'capture'}
            onPress={() => setActiveTab('capture')}
          />
          <TabButton
            label="図鑑"
            icon="DEX"
            active={activeTab === 'encyclopedia'}
            onPress={() => setActiveTab('encyclopedia')}
          />
        </View>
      </View>
    </SafeAreaView>
  );
}

function HudStat({ label, value, accent = false }: { label: string; value: string; accent?: boolean }) {
  return (
    <View style={[styles.hudStat, accent && styles.hudStatAccent]}>
      <Text style={[styles.hudValue, accent && styles.hudValueAccent]}>{value}</Text>
      <Text style={styles.hudLabel}>{label}</Text>
    </View>
  );
}

function TabButton({
  label,
  icon,
  active,
  onPress,
}: {
  label: string;
  icon: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.tabButton,
        active && styles.tabButtonActive,
        pressed && { opacity: 0.75 },
      ]}
    >
      <Text style={[styles.tabIcon, active && styles.tabTextActive]}>{icon}</Text>
      <Text style={[styles.tabText, active && styles.tabTextActive]}>{label}</Text>
    </Pressable>
  );
}

const styles = {
  screen: {
    flex: 1,
    backgroundColor: '#07131d',
  },
  appFrame: {
    flex: 1,
    width: '100%' as const,
    maxWidth: 780,
    alignSelf: 'center' as const,
    backgroundColor: '#edf4ef',
  },
  header: {
    flexDirection: 'row' as const,
    alignItems: 'center' as const,
    gap: 12,
    paddingHorizontal: 18,
    paddingTop: 18,
    paddingBottom: 16,
    backgroundColor: '#102233',
    borderBottomWidth: 1,
    borderBottomColor: '#244763',
    overflow: 'hidden' as const,
  },
  headerGlow: {
    position: 'absolute' as const,
    left: -90,
    top: -80,
    width: 240,
    height: 210,
    borderRadius: 120,
    backgroundColor: 'rgba(45, 196, 147, 0.22)',
  },
  kicker: {
    color: '#68dcb0',
    fontSize: 11,
    fontWeight: '900' as const,
    letterSpacing: 0,
  },
  title: {
    color: '#f7fbff',
    fontSize: 25,
    fontWeight: '900' as const,
    letterSpacing: 0,
    marginTop: 2,
  },
  subtitle: {
    color: '#b9ceda',
    fontSize: 13,
    marginTop: 4,
  },
  progressTrack: {
    height: 8,
    borderRadius: 8,
    overflow: 'hidden' as const,
    backgroundColor: 'rgba(255,255,255,0.16)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.18)',
  },
  progressFill: {
    height: '100%' as const,
    borderRadius: 8,
    backgroundColor: '#ffd24a',
  },
  progressBadge: {
    minWidth: 88,
    minHeight: 72,
    borderRadius: 8,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    backgroundColor: '#f7fbff',
    borderWidth: 3,
    borderColor: '#ffd24a',
  },
  progressValue: {
    color: '#102233',
    fontSize: 19,
    fontWeight: '900' as const,
  },
  progressLabel: {
    color: '#4f6678',
    fontSize: 9,
    fontWeight: '900' as const,
    marginTop: 1,
  },
  hudRow: {
    flexDirection: 'row' as const,
    gap: 8,
    paddingHorizontal: 14,
    paddingTop: 12,
    backgroundColor: '#102233',
  },
  hudStat: {
    flex: 1,
    minHeight: 58,
    borderRadius: 8,
    justifyContent: 'center' as const,
    paddingHorizontal: 12,
    backgroundColor: '#173149',
    borderWidth: 1,
    borderColor: '#294f6f',
  },
  hudStatAccent: {
    backgroundColor: '#2e263f',
    borderColor: '#7f67c9',
  },
  hudValue: {
    color: '#f7fbff',
    fontSize: 17,
    fontWeight: '900' as const,
  },
  hudValueAccent: {
    color: '#ffd24a',
  },
  hudLabel: {
    color: '#9fb6c8',
    fontSize: 10,
    fontWeight: '900' as const,
    marginTop: 2,
  },
  statusBar: {
    paddingHorizontal: 18,
    paddingVertical: 10,
    backgroundColor: '#163047',
    borderBottomWidth: 1,
    borderBottomColor: '#244763',
  },
  statusText: {
    color: '#dcebf3',
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '700' as const,
  },
  content: {
    flex: 1,
    padding: 14,
    backgroundColor: '#edf4ef',
  },
  selectionPanel: {
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#b8cad1',
    backgroundColor: '#ffffff',
    padding: 16,
    gap: 4,
    boxShadow: '0 7px 14px rgba(16, 34, 51, 0.10)',
  },
  selectionPanelActive: {
    borderColor: '#ffd24a',
    backgroundColor: '#fffdf3',
  },
  selectionHeader: {
    flexDirection: 'row' as const,
    gap: 10,
    alignItems: 'flex-start' as const,
  },
  selectionEyebrow: {
    color: '#2b86c5',
    fontSize: 10,
    fontWeight: '900' as const,
    letterSpacing: 0,
  },
  selectionTitle: {
    color: '#102233',
    fontSize: 18,
    fontWeight: '900' as const,
    marginTop: 2,
  },
  selectionText: {
    color: '#4f6678',
    lineHeight: 19,
    fontSize: 13,
    fontWeight: '600' as const,
  },
  rarityMedal: {
    overflow: 'hidden' as const,
    borderRadius: 8,
    backgroundColor: '#e85d4f',
    color: '#ffffff',
    fontSize: 10,
    fontWeight: '900' as const,
    paddingHorizontal: 8,
    paddingVertical: 6,
  },
  tabBar: {
    flexDirection: 'row' as const,
    gap: 10,
    padding: 12,
    paddingBottom: 14,
    backgroundColor: '#102233',
    borderTopWidth: 1,
    borderTopColor: '#244763',
  },
  tabButton: {
    flex: 1,
    minHeight: 56,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    borderRadius: 8,
    backgroundColor: '#173149',
    borderWidth: 1,
    borderColor: '#294f6f',
  },
  tabButtonActive: {
    backgroundColor: '#e85d4f',
    borderColor: '#ffd24a',
  },
  tabIcon: {
    color: '#9fb6c8',
    fontSize: 10,
    fontWeight: '900' as const,
    marginBottom: 2,
  },
  tabText: {
    color: '#dcebf3',
    fontWeight: '800' as const,
  },
  tabTextActive: {
    color: '#ffffff',
  },
};
