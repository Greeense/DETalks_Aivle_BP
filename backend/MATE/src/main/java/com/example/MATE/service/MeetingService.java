package com.example.MATE.service;

import com.example.MATE.dto.MeetingDetailDto;
import com.example.MATE.dto.MeetingDto;
import com.example.MATE.model.*;
import com.example.MATE.repository.MeetingParticipantRepository;
import com.example.MATE.repository.MeetingRepository;
import com.example.MATE.repository.SummaryRepository;
import com.example.MATE.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final UserRepository userRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final SummaryRepository summaryRepository;

    public List<Meeting> getAllMeetings() {
        return meetingRepository.findAllMeetings();
    }

    // 유저 ID 를 받아 해당 유저가 참여한 모든 미팅 ID 를 반환
    public List<Integer> getMeetingIdsByUserId(Integer userId) {
        return meetingRepository.findMeetingIdsByUserId(userId);
    }

    // 유저 ID 를 받아 해당 유저가 참여한 모든 미팅 ID 를 반환
    public List<Integer> getMeetingIdsByUserIdIncludingDone(Integer userId) {
        return meetingRepository.findMeetingIdsByUserIdIncludingDone(userId);
    }


    // 유저 Id를 받아 해당 유저가 참여한 모든 Meeting 객체를 바로 반환 (
    public Page<Meeting> getMeetingsByUserIdWithPaging(Integer userId, Pageable pageable) {
        return meetingRepository.findByMeetingParticipants_User_UserId(userId, pageable);
    }

    // 미팅 ID 를 받아 해당 미팅의 정보를 반환
    public Meeting getMeetingByMeetingId(Integer meetingId) {
        return meetingRepository.findByMeetingId(meetingId);
    }

    //새로운 회의생성 + 회의참여자 추가
    @Transactional
    public MeetingDto createMeeting(MeetingDto meetingDto, Integer userId) {
        System.out.println(">>> [MeetingService/createMeeting] 실행!");

        Meeting meeting;
        //url 중복여부확인
        if( meetingRepository.existsByUrl(meetingDto.getMeetingUrl()) ){
            System.out.println(">>> [MeetingService] 기존 회의가 존재하므로 새로 생성하지 않고 기존 회의 사용");
            meeting = meetingRepository.findByUrl(meetingDto.getMeetingUrl())
                    .orElseThrow(() -> new IllegalArgumentException("회의 데이터를 찾을 수 없음"));
        }else{
            //Dto-> entity 변환 후 DB 저장
            meeting = meetingDto.toEntity();
//            meeting.setStartTime(LocalDateTime.now());
            meeting.setCreatedAt(LocalDateTime.now());
            meeting.setEndTime(null);
            meeting.setLastBreakTime(null);
            meeting.setFilepath("");

            //저장
            meeting = meetingRepository.save(meeting);

            if (meeting.getMeetingId() == null) {
                throw new IllegalArgumentException("회의가 정상적으로 저장되지 않음");
            }
        }
        //사용자 조회
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자("+userId+")는 존재하지 않습니다."));
        //회의에 참가한 사람이니?
        if(meetingParticipantRepository.existsByMeetingAndUser(meeting, user)){
            System.out.println(">>> [MeetingService] 해당 사용자는 이미 회의("+meetingDto.getMeetingId()+")에 참여중");
            throw new IllegalArgumentException("이미 회의에 참여 중입니다.");
        }
        //MeetingParticipant에 저장
        MeetingParticipant participant = new MeetingParticipant();
        participant.setMeeting(meeting);
        participant.setUser(user);
        participant.setRecording(true);

        meetingParticipantRepository.save(participant);
        System.out.println("[MeetingService/createMeeting] 회의 및 참가자 저장 완료");
        //entity->Dto 반환
        return MeetingDto.fromEntity(meeting);
    }


    public List<MeetingParticipant> getParticipantsByMeetingId(Integer meetingId) {
        return meetingParticipantRepository.findByMeeting_MeetingId(meetingId);
    }

    @Transactional(readOnly = true)
    public MeetingDetailDto getMeetingDetailById(Integer meetingId) {
        Optional<Meeting> meetingOptional = meetingRepository.findById(meetingId);
        if (meetingOptional.isEmpty()) {
            return null;
        }
        Meeting meetingDetail = meetingOptional.get();

        Optional<Summary> summary = summaryRepository.findByMeeting(meetingDetail);

        return MeetingDetailDto.fromEntity(meetingDetail, summary.orElse(null));

    }

    // 회의 URL로 Meeting 조회
    @Transactional
    public String getMeetingNameByUrl(String meetingUrl) {
        System.out.println(">>> [MeetingService/getMeetingByUrl] 실행! : " + meetingUrl);

        Optional<Meeting> meetingOptional = meetingRepository.findByUrl(meetingUrl);
        String meetingName = "";

        if (meetingOptional.isPresent()) {
            Meeting meeting = meetingOptional.get();
            System.out.println(">>> [MeetingService] 기존 회의 정보 찾음: " + meeting);
            meetingName = meeting.getMeetingName();
        } else {
            System.out.println(">>> [MeetingService] 해당 URL의 회의가 없음. 기본값 반환");
            meetingName = "";
        }

        return meetingName;
    }

    public void startMeeting(Integer meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElse(new Meeting());

        // 현재 시간으로 시작 시간 설정
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedStartTime = now.format(formatter);

        // 문자열을 LocalDateTime으로 변환
        meeting.setStartTime(LocalDateTime.parse(formattedStartTime, formatter));

        meetingRepository.save(meeting); // 저장 또는 업데이트
    }

    public void takeBreak(Integer meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElse(new Meeting());

        // 현재 시간으로 마지막 휴식 시간 설정
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedBreakTime = now.format(formatter);

        // 문자열을 LocalDateTime으로 변환
        meeting.setLastBreakTime(LocalDateTime.parse(formattedBreakTime, formatter));

        meetingRepository.save(meeting); // 저장 또는 업데이트
    }

    public void endMeeting(Integer meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElse(new Meeting());

        // 현재 시간으로 종료 시간 설정
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedEndTime = now.format(formatter);

        // 문자열을 LocalDateTime으로 변환
        meeting.setEndTime(LocalDateTime.parse(formattedEndTime, formatter));

        meetingRepository.save(meeting); // 저장 또는 업데이트
    }
}
