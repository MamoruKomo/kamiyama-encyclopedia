import { CameraView, useCameraPermissions } from 'expo-camera';
import * as Location from 'expo-location';
import { useMemo, useRef, useState } from 'react';
import { Image, Pressable, ScrollView, Text, TextInput, View } from 'react-native';

import { speciesCandidates } from '../data/kamiyama';
import { describeEnvironment, inferRarity, suggestCandidates } from '../lib/geo';
import type { LatLng, Observation, Rarity, SpeciesCandidate, SpeciesCategory } from '../types/domain';

type CapturePanelProps = {
  currentLocation: LatLng | null;
  onObservationSaved: (observation: Observation) => void;
  onLocationResolved: (location: LatLng) => void;
};

type CapturedPhoto = {
  uri: string;
  location: LatLng;
  accuracy: number | null;
  observedAt: Date;
};

const rarityLabel: Record<Rarity, string> = {
  common: 'COMMON',
  uncommon: 'UNCOMMON',
  rare: 'RARE',
  special: 'SPECIAL',
};

const rarityColor: Record<Rarity, string> = {
  common: '#2fb088',
  uncommon: '#2b86c5',
  rare: '#8c68d8',
  special: '#e85d4f',
};

export function CapturePanel({
  currentLocation,
  onObservationSaved,
  onLocationResolved,
}: CapturePanelProps) {
  const cameraRef = useRef<CameraView>(null);
  const [permission, requestPermission] = useCameraPermissions();
  const [category, setCategory] = useState<SpeciesCategory>('plant');
  const [photo, setPhoto] = useState<CapturedPhoto | null>(null);
  const [selectedCandidate, setSelectedCandidate] = useState<SpeciesCandidate | null>(null);
  const [customName, setCustomName] = useState('');
  const [note, setNote] = useState('');
  const [isBusy, setIsBusy] = useState(false);
  const [message, setMessage] = useState('写真を撮ると、GPSと時刻から観察ピンを作ります。');

  const suggestions = useMemo(() => {
    if (!photo) {
      return [];
    }
    return suggestCandidates(speciesCandidates, category, photo.location, photo.observedAt);
  }, [category, photo]);

  async function takePhoto() {
    if (!permission?.granted) {
      await requestPermission();
      return;
    }

    setIsBusy(true);
    setMessage('カメラと位置情報を確認しています。');
    try {
      const location = await resolveLocation();
      const captured = await cameraRef.current?.takePictureAsync({
        base64: false,
        quality: 0.72,
        skipProcessing: false,
      });

      if (!captured?.uri) {
        throw new Error('写真を取得できませんでした。');
      }

      const nextPhoto = {
        uri: captured.uri,
        location,
        accuracy: null,
        observedAt: new Date(),
      };
      setPhoto(nextPhoto);
      setSelectedCandidate(null);
      setCustomName('');
      setMessage('候補を選ぶか、名前を入力して図鑑に登録できます。');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '撮影に失敗しました。');
    } finally {
      setIsBusy(false);
    }
  }

  async function save() {
    if (!photo) {
      return;
    }

    const observedAt = photo.observedAt;
    const name = customName.trim() || selectedCandidate?.commonName || '未同定';
    const location = photo.location;
    const observation: Observation = {
      id: `${observedAt.getTime()}-${Math.random().toString(36).slice(2)}`,
      photoUri: photo.uri,
      category,
      candidateId: selectedCandidate?.id ?? null,
      customName: name,
      note: note.trim(),
      latitude: location.latitude,
      longitude: location.longitude,
      accuracy: photo.accuracy,
      observedAt: observedAt.toISOString(),
      environment: describeEnvironment(location),
      rarity: inferRarity(selectedCandidate, location, observedAt),
    };

    await onObservationSaved(observation);
    setPhoto(null);
    setSelectedCandidate(null);
    setCustomName('');
    setNote('');
    setMessage('図鑑に登録しました。地図にあなたの発見ピンが増えています。');
  }

  async function resolveLocation(): Promise<LatLng> {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') {
      if (currentLocation) {
        return currentLocation;
      }
      throw new Error('位置情報の許可がないため、観察地点を記録できません。');
    }
    const result = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Balanced,
    });
    const location = {
      latitude: result.coords.latitude,
      longitude: result.coords.longitude,
    };
    onLocationResolved(location);
    return location;
  }

  return (
    <ScrollView contentContainerStyle={{ paddingBottom: 120 }}>
      <View style={{ gap: 14 }}>
        <View style={styles.scannerHero}>
          <Text style={styles.scannerKicker}>FIELD SCANNER</Text>
          <Text style={styles.scannerTitle}>いま見つけた一瞬を登録</Text>
          <Text style={styles.scannerText}>
            写真、GPS、時刻をまとめて保存します。候補は場所と季節から近い順に表示されます。
          </Text>
        </View>

        <View style={{ flexDirection: 'row', gap: 8 }}>
          <Pressable
            onPress={() => setCategory('plant')}
            style={({ pressed }) => [
              styles.segment,
              category === 'plant' && styles.segmentActive,
              pressed && styles.pressed,
            ]}
          >
            <Text style={[styles.segmentText, category === 'plant' && styles.segmentTextActive]}>
              植物
            </Text>
            <Text style={[styles.segmentSubText, category === 'plant' && styles.segmentTextActive]}>
              LEAF
            </Text>
          </Pressable>
          <Pressable
            onPress={() => setCategory('insect')}
            style={({ pressed }) => [
              styles.segment,
              category === 'insect' && styles.segmentActive,
              pressed && styles.pressed,
            ]}
          >
            <Text style={[styles.segmentText, category === 'insect' && styles.segmentTextActive]}>
              虫
            </Text>
            <Text style={[styles.segmentSubText, category === 'insect' && styles.segmentTextActive]}>
              INSECT
            </Text>
          </Pressable>
        </View>

        <View style={styles.cameraBox}>
          <View style={styles.cameraFrameTop} />
          <View style={styles.cameraFrameBottom} />
          {permission?.granted ? (
            <CameraView ref={cameraRef} style={styles.camera} facing="back" animateShutter />
          ) : (
            <View style={styles.permissionBox}>
              <Text style={styles.permissionText}>カメラの許可が必要です。</Text>
              <Pressable onPress={requestPermission} style={styles.primaryButton}>
                <Text style={styles.primaryButtonText}>許可する</Text>
              </Pressable>
            </View>
          )}
        </View>

        <Pressable
          onPress={takePhoto}
          disabled={isBusy}
          style={({ pressed }) => [
            styles.shutterButton,
            (pressed || isBusy) && styles.pressed,
          ]}
        >
          <View style={styles.shutterCore}>
            <Text style={styles.shutterButtonText}>{isBusy ? '記録中...' : '撮影して地点を記録'}</Text>
          </View>
        </Pressable>

        <Text style={styles.message}>{message}</Text>

        {photo ? (
          <View style={styles.reviewPanel}>
            <Image source={{ uri: photo.uri }} style={styles.previewImage} />
            <View style={styles.captureMetaPanel}>
              <Text style={styles.metaLabel}>CAPTURE DATA</Text>
              <Text style={styles.metaText}>
                {photo.observedAt.toLocaleString('ja-JP')} / {describeEnvironment(photo.location)}
              </Text>
              <Text style={styles.metaText}>
                {photo.location.latitude.toFixed(5)}, {photo.location.longitude.toFixed(5)}
              </Text>
            </View>

            <Text style={styles.sectionTitle}>候補</Text>
            <View style={{ gap: 8 }}>
              {suggestions.map(({ candidate, distance }) => (
                <Pressable
                  key={candidate.id}
                  onPress={() => {
                    setSelectedCandidate(candidate);
                    setCustomName(candidate.commonName);
                  }}
                  style={[
                    styles.candidateRow,
                    selectedCandidate?.id === candidate.id && styles.candidateRowActive,
                  ]}
                >
                  <View style={{ flex: 1 }}>
                    <View style={styles.candidateTitleRow}>
                      <Text style={styles.candidateName}>{candidate.commonName}</Text>
                      <Text
                        style={[
                          styles.candidateRarity,
                          { backgroundColor: rarityColor[candidate.rarity] },
                        ]}
                      >
                        {rarityLabel[candidate.rarity]}
                      </Text>
                    </View>
                    <Text style={styles.candidateSci}>{candidate.scientificName}</Text>
                    <Text style={styles.candidateHint}>{candidate.hint}</Text>
                  </View>
                  <Text style={styles.distanceText}>{Math.round(distance)}m</Text>
                </Pressable>
              ))}
            </View>

            <TextInput
              value={customName}
              onChangeText={setCustomName}
              placeholder="生き物名"
              style={styles.input}
            />
            <TextInput
              value={note}
              onChangeText={setNote}
              placeholder="メモ"
              multiline
              style={[styles.input, styles.noteInput]}
            />
            <Pressable onPress={save} style={styles.primaryButton}>
              <Text style={styles.primaryButtonText}>図鑑に登録</Text>
            </Pressable>
          </View>
        ) : null}
      </View>
    </ScrollView>
  );
}

const styles = {
  scannerHero: {
    borderRadius: 8,
    padding: 16,
    backgroundColor: '#102233',
    borderWidth: 1,
    borderColor: '#244763',
    gap: 4,
  },
  scannerKicker: {
    color: '#68dcb0',
    fontSize: 10,
    fontWeight: '900' as const,
    letterSpacing: 0,
  },
  scannerTitle: {
    color: '#f7fbff',
    fontSize: 20,
    fontWeight: '900' as const,
  },
  scannerText: {
    color: '#b9ceda',
    fontSize: 13,
    lineHeight: 19,
    fontWeight: '600' as const,
  },
  segment: {
    flex: 1,
    minHeight: 54,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#b8cad1',
    backgroundColor: '#ffffff',
  },
  segmentActive: {
    backgroundColor: '#2b86c5',
    borderColor: '#ffd24a',
  },
  segmentText: {
    color: '#102233',
    fontSize: 16,
    fontWeight: '900' as const,
  },
  segmentSubText: {
    color: '#6b7f8f',
    fontSize: 10,
    fontWeight: '900' as const,
    marginTop: 2,
  },
  segmentTextActive: {
    color: '#ffffff',
  },
  cameraBox: {
    height: 330,
    overflow: 'hidden' as const,
    borderRadius: 8,
    backgroundColor: '#06101a',
    borderWidth: 2,
    borderColor: '#173149',
    position: 'relative' as const,
  },
  cameraFrameTop: {
    position: 'absolute' as const,
    left: 18,
    right: 18,
    top: 18,
    height: 1,
    backgroundColor: 'rgba(255, 210, 74, 0.72)',
    zIndex: 2,
  },
  cameraFrameBottom: {
    position: 'absolute' as const,
    left: 18,
    right: 18,
    bottom: 18,
    height: 1,
    backgroundColor: 'rgba(104, 220, 176, 0.72)',
    zIndex: 2,
  },
  camera: {
    flex: 1,
  },
  permissionBox: {
    flex: 1,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    gap: 16,
    padding: 24,
  },
  permissionText: {
    color: '#f7faf5',
    fontSize: 16,
  },
  shutterButton: {
    minHeight: 62,
    borderRadius: 8,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    backgroundColor: '#102233',
    borderWidth: 2,
    borderColor: '#ffd24a',
    padding: 6,
    shadowColor: '#102233',
    shadowOpacity: 0.18,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 7 },
  },
  shutterCore: {
    width: '100%' as const,
    minHeight: 46,
    borderRadius: 8,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    backgroundColor: '#e85d4f',
  },
  shutterButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '800' as const,
  },
  primaryButton: {
    minHeight: 48,
    borderRadius: 8,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    backgroundColor: '#2b86c5',
    paddingHorizontal: 16,
  },
  primaryButtonText: {
    color: '#ffffff',
    fontSize: 15,
    fontWeight: '800' as const,
  },
  pressed: {
    opacity: 0.75,
  },
  message: {
    color: '#4f6678',
    lineHeight: 20,
    fontWeight: '700' as const,
  },
  reviewPanel: {
    gap: 12,
    paddingTop: 4,
  },
  previewImage: {
    width: '100%' as const,
    aspectRatio: 1,
    borderRadius: 8,
    backgroundColor: '#dce8ea',
    borderWidth: 2,
    borderColor: '#102233',
  },
  captureMetaPanel: {
    borderRadius: 8,
    padding: 12,
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#b8cad1',
  },
  metaLabel: {
    color: '#2b86c5',
    fontSize: 10,
    fontWeight: '900' as const,
    marginBottom: 4,
  },
  metaText: {
    color: '#4f6678',
    fontSize: 13,
    fontWeight: '700' as const,
  },
  sectionTitle: {
    color: '#102233',
    fontSize: 17,
    fontWeight: '900' as const,
  },
  candidateRow: {
    flexDirection: 'row' as const,
    gap: 12,
    padding: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#b8cad1',
    backgroundColor: '#ffffff',
  },
  candidateRowActive: {
    borderColor: '#ffd24a',
    backgroundColor: '#fffdf3',
  },
  candidateTitleRow: {
    flexDirection: 'row' as const,
    alignItems: 'center' as const,
    gap: 8,
  },
  candidateName: {
    color: '#102233',
    fontWeight: '900' as const,
    fontSize: 15,
    flexShrink: 1,
  },
  candidateRarity: {
    overflow: 'hidden' as const,
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 3,
    color: '#ffffff',
    fontSize: 9,
    fontWeight: '900' as const,
  },
  candidateSci: {
    color: '#6b7f8f',
    fontSize: 12,
    fontStyle: 'italic' as const,
    marginTop: 2,
  },
  candidateHint: {
    color: '#405465',
    marginTop: 6,
    lineHeight: 18,
  },
  distanceText: {
    color: '#2b86c5',
    fontWeight: '900' as const,
    minWidth: 56,
    textAlign: 'right' as const,
  },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderColor: '#b8cad1',
    borderRadius: 8,
    paddingHorizontal: 12,
    fontSize: 15,
    backgroundColor: '#ffffff',
  },
  noteInput: {
    minHeight: 88,
    paddingTop: 12,
  },
};
