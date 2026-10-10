<?php

declare(strict_types=1);

namespace App\Controller\Admin;

use App\Form\ReplaceLearningTeamCourseType;
use App\LearningTeam\ReplaceSubscription;
use App\LearningTeam\StudentLearningTeamResolver;
use App\Repository\LearningTeamParticipantRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class LearningTeamController extends AbstractController
{
    #[Route('/admin/learning-team/{id}/change-course', name: 'admin_learning_team_change_course')]
    public function changeCourseAction(
        Request $request,
        ReplaceSubscription $replaceSubscription,
        LearningTeamParticipantRepository $learningTeamParticipantRepository,
        int $id
    ): Response {
        $participant = $learningTeamParticipantRepository->find($id);
        if (null === $participant) {
            throw $this->createNotFoundException('Participant not found.');
        }

        $form = $this->createForm(ReplaceLearningTeamCourseType::class)->handleRequest($request);

        if ($form->isSubmitted() && $form->isValid()) {
            $replaceSubscription->replace($participant->getLearningTeam(), $form->getData()['courseCode']);
            $this->addFlash('success', 'Course replaced.');

            return $this->redirectToRoute('admin_learning_team_show', ['id' => $id]);
        }

        return $this->render('admin/learning_team/change_course.html.twig', [
            'form' => $form->createView(),
        ]);
    }

    #[Route('/admin/learning-team/replace-course', name: 'admin_learning_team_replace_course')]
    public function replaceLearningTeamCourseAction(
        Request $request,
        ReplaceSubscription $replaceSubscription,
        LearningTeamParticipantRepository $learningTeamParticipantRepository,
        StudentLearningTeamResolver $studentLearningTeamResolver
    ): Response {
    }
}
