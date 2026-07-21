import 'leaflet/dist/leaflet.css';
import '../styles/web-map.css';

import L from 'leaflet';
import { useState } from 'react';
import { MapContainer, Marker, Polygon, Popup, TileLayer } from 'react-leaflet';

import { KAMIYAMA_CENTER, natureZones, speciesCandidates } from '../data/kamiyama';
import type { Observation, Rarity, SpeciesCategory } from '../types/domain';

type MapPanelProps = {
  observations: Observation[];
  currentLocation: { latitude: number; longitude: number } | null;
  onSelectObservation: (observation: Observation) => void;
};

type MapFilter = 'all' | SpeciesCategory;

const zoneColors = {
  river: '#5B9FCC',
  forest: '#4F805A',
  village: '#C89358',
  ridge: '#7A8F61',
  field: '#A7A64C',
};

const rarityColor: Record<Rarity, string> = {
  common: '#4F805A',
  uncommon: '#4D85C5',
  rare: '#EAA52A',
  special: '#C95B4A',
};

const rarityLabel: Record<Rarity, string> = {
  common: 'よく見つかる',
  uncommon: 'ちょっと珍しい',
  rare: 'レア',
  special: 'とくべつ',
};

export function MapPanel({ observations, currentLocation, onSelectObservation }: MapPanelProps) {
  const [filter, setFilter] = useState<MapFilter>('all');
  const visibleObservations = observations.filter((item) => filter === 'all' || item.category === filter);
  const visibleCandidates = speciesCandidates.filter((item) => filter === 'all' || item.category === filter);

  return (
    <div className="map-panel">
      <div className="map-filter-row" aria-label="地図フィルター">
        <button className={filter === 'all' ? 'active' : ''} onClick={() => setFilter('all')}>すべて</button>
        <button className={filter === 'plant' ? 'active' : ''} onClick={() => setFilter('plant')}>植物</button>
        <button className={filter === 'insect' ? 'active' : ''} onClick={() => setFilter('insect')}>昆虫</button>
        <span>{visibleObservations.length}件の発見</span>
      </div>
      <div className="map-shell">
        <MapContainer
          center={[KAMIYAMA_CENTER.latitude, KAMIYAMA_CENTER.longitude]}
          zoom={13}
          minZoom={11}
          scrollWheelZoom
          className="kamiyama-map"
        >
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />

          {natureZones.map((zone) => (
            <Polygon
              key={zone.id}
              pathOptions={{
                color: zoneColors[zone.kind],
                fillColor: zoneColors[zone.kind],
                fillOpacity: 0.1,
                opacity: 0.5,
                weight: 1.5,
              }}
              positions={zone.polygon.map((point) => [point.latitude, point.longitude])}
            >
              <Popup><strong>{zone.name}</strong><br />{zone.description}</Popup>
            </Polygon>
          ))}

          {visibleCandidates.flatMap((candidate) => candidate.knownLocations.map((location, index) => (
            <Marker key={`${candidate.id}-${index}`} icon={knownIcon(candidate.category)} position={[location.latitude, location.longitude]}>
              <Popup><strong>{candidate.commonName}</strong><br />{candidate.scientificName}<br />公開生物データの周辺記録</Popup>
            </Marker>
          )))}

          {visibleObservations.map((observation) => (
            <Marker
              key={observation.id}
              icon={observationIcon(observation.rarity, observation.category)}
              position={[observation.latitude, observation.longitude]}
              eventHandlers={{ click: () => onSelectObservation(observation) }}
            >
              <Popup>
                <strong>{observation.customName || '名前を確認中'}</strong><br />
                {rarityLabel[observation.rarity]}<br />
                {new Date(observation.observedAt).toLocaleString('ja-JP')}<br />
                {observation.environment}
              </Popup>
            </Marker>
          ))}

          {currentLocation ? (
            <Marker icon={currentIcon} position={[currentLocation.latitude, currentLocation.longitude]}>
              <Popup>いまいる場所</Popup>
            </Marker>
          ) : null}
        </MapContainer>
        <div className="map-legend">
          <span><i className="legend-found" />発見</span>
          <span><i className="legend-known" />周辺記録</span>
          <span><i className="legend-current" />現在地</span>
        </div>
      </div>
    </div>
  );
}

function observationIcon(rarity: Rarity, category: SpeciesCategory) {
  return L.divIcon({
    className: `observation-pin observation-${rarity} ${category}`,
    html: `<span style="background:${rarityColor[rarity]}"><i>${category === 'plant' ? '葉' : '虫'}</i></span><b></b>`,
    iconSize: [42, 48],
    iconAnchor: [21, 46],
  });
}

function knownIcon(category: SpeciesCategory) {
  return L.divIcon({
    className: `known-pin ${category}`,
    html: `<span><i>${category === 'plant' ? '葉' : '虫'}</i></span>`,
    iconSize: [32, 38],
    iconAnchor: [16, 36],
  });
}

const currentIcon = L.divIcon({
  className: 'current-pin',
  html: '<span></span>',
  iconSize: [30, 30],
  iconAnchor: [15, 15],
});
