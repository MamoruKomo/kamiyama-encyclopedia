import * as Location from 'expo-location';
import { useEffect, useMemo, useState } from 'react';
import { SafeAreaView, StatusBar, Text, Pressable, View } from 'react-native';

import { CapturePanel } from './components/CapturePanel';
import { EncyclopediaPanel } from './components/EncyclopediaPanel';
import { MapPanel } from './components/MapPanel';
import { KAMIYAMA_CENTER, speciesCandidates } from './data/kamiyama';
import { deleteObservation, loadObservations, saveObservation } from './lib/storage';
import {
  clearThinkletObservationParam,
  hasThinkletObservationParam,
  readThinkletObservationFromUrl,
} from './lib/thinkletImport';
import type { LatLng, Observation } from './types/domain';

type Tab = 'map' | 'capture' | 'encyclopedia';

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('map');
  const [observations, setObservations] = useState<Observation[]>([]);
  const [currentLocation, setCurrentLocation] = useState<LatLng | null>(null);
  const [selectedObservation, setSelectedObservation] = useState<Observation | null>(null);
  const [statusText, setStatusText] = useState('神山町周辺の軽い範囲で探索を開始できます。');

  useEffect(() => {
    const hasIncomingThinkletObservation = hasThinkletObservationParam();
    loadObservations()
      .then(async (loadedObservations) => {
        const thinkletObservation = readThinkletObservationFromUrl();
        if (!thinkletObservation) {
          setObservations(loadedObservations);
          return;
        }

        const alreadyImported = loadedObservations.some(
          (observation) => observation.id === thinkletObservation.id,
        );
        if (!alreadyImported) {
          await saveObservation(thinkletObservation);
          setObservations([thinkletObservation, ...loadedObservations]);
          setSelectedObservation(thinkletObservation);
          setCurrentLocation({
            latitude: thinkletObservation.latitude,
            longitude: thinkletObservation.longitude,
          });
          setStatusText('THINKLETから観察を取り込みました。');
        } else {
          setObservations(loadedObservations);
          setSelectedObservation(thinkletObservation);
          setStatusText('THINKLET観察はすでに取り込み済みです。');
        }
        clearThinkletObservationParam();
      })
      .catch(() => setStatusText('ローカル図鑑の読み込みに失敗しました。'));
    resolveInitialLocation({ preserveStatus: hasIncomingThinkletObservation });
  }, []);

  const discoveredCandidateIds = useMemo(
    () => new Set(observations.map((item) => item.candidateId).filter(Boolean)),
    [observations],
  );

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
      <StatusBar barStyle="dark-content" />
      <View style={styles.appFrame}>
        <View style={styles.header}>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>神山生物図鑑</Text>
            <Text style={styles.subtitle}>植物と虫を撮って、自分だけの分布図を育てる</Text>
          </View>
          <View style={styles.progressBadge}>
            <Text style={styles.progressValue}>
              {discoveredCandidateIds.size}/{speciesCandidates.length}
            </Text>
            <Text style={styles.progressLabel}>候補種</Text>
          </View>
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
                <View style={styles.selectionPanel}>
                  <Text style={styles.selectionTitle}>{selectedObservation.customName}</Text>
                  <Text style={styles.selectionText}>
                    {new Date(selectedObservation.observedAt).toLocaleString('ja-JP')} /{' '}
                    {selectedObservation.environment}
                  </Text>
                  <Text style={styles.selectionText}>
                    レア度: {selectedObservation.rarity.toUpperCase()}
                  </Text>
                </View>
              ) : (
                <View style={styles.selectionPanel}>
                  <Text style={styles.selectionTitle}>探索マップ</Text>
                  <Text style={styles.selectionText}>
                    緑や水辺のレイヤーは観察したい環境の目安です。薄い既知ピンはGBIF由来の周辺記録、濃いピンはあなたの発見です。
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
            active={activeTab === 'map'}
            onPress={() => setActiveTab('map')}
          />
          <TabButton
            label="撮影"
            active={activeTab === 'capture'}
            onPress={() => setActiveTab('capture')}
          />
          <TabButton
            label="図鑑"
            active={activeTab === 'encyclopedia'}
            onPress={() => setActiveTab('encyclopedia')}
          />
        </View>
      </View>
    </SafeAreaView>
  );
}

function TabButton({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.tabButton,
        active && styles.tabButtonActive,
        pressed && { opacity: 0.75 },
      ]}
    >
      <Text style={[styles.tabText, active && styles.tabTextActive]}>{label}</Text>
    </Pressable>
  );
}

const styles = {
  screen: {
    flex: 1,
    backgroundColor: '#e8ede6',
  },
  appFrame: {
    flex: 1,
    width: '100%' as const,
    maxWidth: 780,
    alignSelf: 'center' as const,
    backgroundColor: '#fbfcf8',
  },
  header: {
    flexDirection: 'row' as const,
    alignItems: 'center' as const,
    gap: 12,
    paddingHorizontal: 18,
    paddingTop: 14,
    paddingBottom: 12,
    backgroundColor: '#fbfcf8',
    borderBottomWidth: 1,
    borderBottomColor: '#dde5dd',
  },
  title: {
    color: '#14231a',
    fontSize: 22,
    fontWeight: '900' as const,
    letterSpacing: 0,
  },
  subtitle: {
    color: '#59675e',
    fontSize: 12,
    marginTop: 4,
  },
  progressBadge: {
    minWidth: 76,
    minHeight: 56,
    borderRadius: 8,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    backgroundColor: '#26372d',
  },
  progressValue: {
    color: '#ffffff',
    fontSize: 18,
    fontWeight: '900' as const,
  },
  progressLabel: {
    color: '#c9d5cb',
    fontSize: 11,
    marginTop: 1,
  },
  statusBar: {
    paddingHorizontal: 18,
    paddingVertical: 10,
    backgroundColor: '#edf3ec',
    borderBottomWidth: 1,
    borderBottomColor: '#d7e0d8',
  },
  statusText: {
    color: '#405146',
    fontSize: 13,
    lineHeight: 18,
  },
  content: {
    flex: 1,
    padding: 14,
  },
  selectionPanel: {
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#d7e0d8',
    backgroundColor: '#ffffff',
    padding: 14,
    gap: 4,
  },
  selectionTitle: {
    color: '#14231a',
    fontSize: 17,
    fontWeight: '900' as const,
  },
  selectionText: {
    color: '#59675e',
    lineHeight: 19,
    fontSize: 13,
  },
  tabBar: {
    flexDirection: 'row' as const,
    gap: 8,
    padding: 12,
    paddingBottom: 14,
    backgroundColor: '#fbfcf8',
    borderTopWidth: 1,
    borderTopColor: '#d7e0d8',
  },
  tabButton: {
    flex: 1,
    minHeight: 46,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    borderRadius: 8,
    backgroundColor: '#edf3ec',
  },
  tabButtonActive: {
    backgroundColor: '#e05a47',
  },
  tabText: {
    color: '#314238',
    fontWeight: '800' as const,
  },
  tabTextActive: {
    color: '#ffffff',
  },
};
