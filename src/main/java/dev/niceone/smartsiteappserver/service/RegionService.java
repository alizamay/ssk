package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.mapper.RegionModelMapper;
import dev.niceone.smartsiteappserver.model.Region;
import dev.niceone.smartsiteappserver.payload.RegionResponse;
import dev.niceone.smartsiteappserver.repository.RegionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RegionService {
    @Autowired
    private RegionRepository regionRepository;

    public List<RegionResponse> listRegion() {
        List<Region> regions = regionRepository.findByActive(true);

        List<RegionResponse> response = new ArrayList<RegionResponse>();
        for (Region region : regions) {
            response.add(RegionModelMapper.mapRegionToRegionResponse(region));
        }

        return response;
    }
}
