import 'leaflet/dist/leaflet.css';
import '../styles/web-map.css';

import L from 'leaflet';
import { MapContainer, Marker, Polygon, Popup, TileLayer } from 'react-leaflet';

import { KAMIYAMA_CENTER, natureZones, speciesCandidates } from '../data/kamiyama';
import type { Observation, Rarity } from '../types/domain';

type MapPanelProps = {
  observations: Observation[];
  currentLocation: { latitude: number; longitude: number } | null;
  onSelectObservation: (observation: Observation) => void;
};

const zoneColors = {
  river: '#4aa3df',
  forest: '#2e7d54',
  village: '#d08745',
  ridge: '#7d6cc8',
  field: '#d0a833',
};

const rarityColor: Record<Rarity, string> = {
  common: '#3f8f65',
  uncommon: '#2d83c4',
  rare: '#b05cc7',
  special: '#e05a47',
};

export function MapPanel({ observations, currentLocation, onSelectObservation }: MapPanelProps) {
  return (
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
              fillOpacity: 0.16,
              opacity: 0.65,
              weight: 2,
            }}
            positions={zone.polygon.map((point) => [point.latitude, point.longitude])}
          >
            <Popup>
              <strong>{zone.name}</strong>
              <br />
              {zone.description}
            </Popup>
          </Polygon>
        ))}

        {speciesCandidates.flatMap((candidate) =>
          candidate.knownLocations.map((location, index) => (
            <Marker
              key={`${candidate.id}-${index}`}
              icon={knownIcon(candidate.category)}
              position={[location.latitude, location.longitude]}
            >
              <Popup>
                <strong>{candidate.commonName}</strong>
                <br />
                {candidate.scientificName}
                <br />
                GBIF由来の周辺記録
              </Popup>
            </Marker>
          )),
        )}

        {observations.map((observation) => (
          <Marker
            key={observation.id}
            icon={observationIcon(observation.rarity)}
            position={[observation.latitude, observation.longitude]}
            eventHandlers={{ click: () => onSelectObservation(observation) }}
          >
            <Popup>
              <strong>{observation.customName || '未同定の観察'}</strong>
              <br />
              {new Date(observation.observedAt).toLocaleString('ja-JP')}
              <br />
              {observation.environment}
            </Popup>
          </Marker>
        ))}

        {currentLocation ? (
          <Marker
            icon={currentIcon}
            position={[currentLocation.latitude, currentLocation.longitude]}
          >
            <Popup>現在地</Popup>
          </Marker>
        ) : null}
      </MapContainer>
    </div>
  );
}

function observationIcon(rarity: Rarity) {
  return L.divIcon({
    className: 'observation-pin',
    html: `<span style="background:${rarityColor[rarity]}"></span>`,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });
}

function knownIcon(category: 'plant' | 'insect') {
  return L.divIcon({
    className: `known-pin ${category}`,
    html: category === 'plant' ? '葉' : '虫',
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  });
}

const currentIcon = L.divIcon({
  className: 'current-pin',
  html: '<span></span>',
  iconSize: [28, 28],
  iconAnchor: [14, 14],
});
